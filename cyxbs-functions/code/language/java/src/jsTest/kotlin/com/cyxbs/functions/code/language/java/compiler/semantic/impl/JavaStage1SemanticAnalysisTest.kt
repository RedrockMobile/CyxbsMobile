package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.frontend.JavaLezerAstFrontend
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaDispatchKind
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaConstructorDelegationKind
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticCallableKind
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticModel
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticType
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSymbolKind
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage1 继承、实例成员、构造器、重载与常用泛型的端到端语义测试。 */
class JavaStage1SemanticAnalysisTest {
  /** 所有字段读取、实例调用和 new 都必须产生最终 binding，成功模型不能含 ERROR。 */
  @Test
  fun bindsFieldsCallsAndConstruction() {
    val result = analyze(
      "Main.java" to """
        class Box {
          static int count = 1;
          int value;
          Box(int value) { this.value = value; }
          int get() { return value; }
        }
        class Main {
          static int run() { Box box = new Box(Box.count); return box.get(); }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    assertTrue(model.expressionTypes.values.none { it == JavaSemanticType.Error })
    assertTrue(model.valueAccesses.values.count { it.symbol in model.fieldDeclarations } >= 3)
    assertTrue(model.selectedCallables.values.any { it.dispatch == JavaDispatchKind.SPECIAL })
    assertTrue(model.selectedCallables.values.any { it.dispatch == JavaDispatchKind.VIRTUAL })
    assertTrue(
      model.callableDeclarations.values
        .filter { it.kind == JavaSemanticCallableKind.CONSTRUCTOR }
        .all { it.erasedDescriptor.endsWith(")V") },
    )
    assertTrue(model.selectedCallables.values.any { it.erasedDescriptor == "(I)V" })
    assertTrue(model.selectedCallables.values.any { it.erasedDescriptor == "()I" })
  }

  /** 字段可被参数遮蔽，但 static 上下文不能使用 this。 */
  @Test
  fun handlesFieldShadowingAndStaticThisError() {
    val success = analyze(
      "Main.java" to "class Main { int value; Main(int value) { this.value = value; } int get() { return value; } }",
    )
    assertTrue(success.isSuccess, success.diagnostics.toString())

    assertDiagnostic(
      analyze("Main.java" to "class Main { static Main bad() { return this; } }"),
      "java.semantic.this_in_static_context",
    )
  }

  /** default、显式 this/super 和 implicit super 都形成构造委托边，this 环稳定拒绝。 */
  @Test
  fun resolvesConstructorDelegationsAndRejectsCycle() {
    val success = analyze(
      "Main.java" to """
        class Base { Base(int value) { } }
        class Child extends Base {
          Child() { this(1); }
          Child(int value) { super(value); }
        }
      """.trimIndent(),
    )
    assertTrue(success.isSuccess, success.diagnostics.toString())
    val delegations = success.value!!.constructorDelegations.values
    assertEquals(3, delegations.size)
    assertEquals(1, delegations.count { it.kind == JavaConstructorDelegationKind.THIS })
    assertEquals(2, delegations.count { it.kind == JavaConstructorDelegationKind.SUPER })

    assertDiagnostic(
      analyze(
        "Main.java" to "class Main { Main() { this(1); } Main(int value) { this(); } }",
      ),
      "java.semantic.constructor_delegation_cycle",
    )
  }

  /** override 复用父虚槽；static hiding 不进入 overriddenMethods。 */
  @Test
  fun assignsStableOverrideSlotsAndSeparatesStaticHiding() {
    val result = analyze(
      "Main.java" to """
        class Base { int value() { return 1; } static int tag() { return 1; } }
        class Child extends Base {
          @Override int value() { return 2; }
          static int tag() { return 2; }
        }
        class Main { static int run(Base base) { return base.value(); } }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = result.value!!
    val valueMethods = model.symbols.values.filter { it.kind == JavaSymbolKind.METHOD && it.name == "value" }
    assertEquals(2, valueMethods.size)
    assertEquals(1, valueMethods.map { model.virtualSlots.getValue(it.id) }.distinct().size)
    val staticTag = model.symbols.values.filter { it.kind == JavaSymbolKind.METHOD && it.name == "tag" }
    assertTrue(staticTag.none { it.id in model.overriddenMethods })
  }

  /** 泛型父方法必须先按子类视角代换参数，再判断 override 与虚槽复用。 */
  @Test
  fun overridesGenericParentAfterOwnerSubstitution() {
    val result = analyze(
      "Main.java" to """
        class A<T> { T value(T input) { return input; } }
        class B extends A<String> {
          @Override String value(String input) { return input; }
        }
        class Main { static String run(A<String> value) { return value.value("ok"); } }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = result.value!!
    val methods = model.symbols.values.filter {
      it.kind == JavaSymbolKind.METHOD && it.name == "value"
    }
    assertEquals(2, methods.size)
    assertEquals(1, methods.map { model.virtualSlots.getValue(it.id) }.distinct().size)

    assertDiagnostic(
      analyze(
        "Main.java" to "class A<T>{ T f(T x){return x;} } " +
          "class B extends A<String>{ @Override Object f(Object x){return x;} }",
      ),
      "java.semantic.override_annotation_mismatch",
    )

    val alphaRenamed = analyze(
      "Main.java" to """
        class A { <T> T id(T value) { return value; } }
        class B extends A { @Override <U> U id(U value) { return value; } }
      """.trimIndent(),
    )
    assertTrue(alphaRenamed.isSuccess, alphaRenamed.diagnostics.toString())
    val alphaModel = alphaRenamed.value!!
    val idMethods = alphaModel.symbols.values.filter {
      it.kind == JavaSymbolKind.METHOD && it.name == "id"
    }
    assertEquals(1, idMethods.map { alphaModel.virtualSlots.getValue(it.id) }.distinct().size)

    assertDiagnostic(
      analyze(
        "Main.java" to """
          class Box<T> { }
          class A { void use(Box<String> value) { } }
          class B extends A { @Override void use(Box<Object> value) { } }
        """.trimIndent(),
      ),
      "java.semantic.override_annotation_mismatch",
    )
  }

  /** final、可见性缩窄、static/instance 冲突及错误 @Override 分别产生稳定诊断。 */
  @Test
  fun rejectsInvalidOverrides() {
    assertDiagnostic(
      analyze("Main.java" to "class A { final int f(){return 1;} } class B extends A { int f(){return 2;} }"),
      "java.semantic.override_final_method",
    )
    assertDiagnostic(
      analyze("Main.java" to "class A { public int f(){return 1;} } class B extends A { int f(){return 2;} }"),
      "java.semantic.override_visibility_reduced",
    )
    assertDiagnostic(
      analyze("Main.java" to "class A { static int f(){return 1;} } class B extends A { int f(){return 2;} }"),
      "java.semantic.static_instance_override_mismatch",
    )
    assertDiagnostic(
      analyze("Main.java" to "class A { @Override int f(){return 1;} }"),
      "java.semantic.override_annotation_mismatch",
    )
  }

  /** exact、primitive widening 与 null 最具体规则可唯一选择，互不相关引用仍歧义。 */
  @Test
  fun selectsMostSpecificOverloads() {
    val result = analyze(
      "Main.java" to """
        class Main {
          static int pick(int value) { return 1; }
          static int pick(long value) { return 2; }
          static int text(Object value) { return 3; }
          static int text(String value) { return 4; }
          static int run() { return pick((1)) + text(null); }
        }
      """.trimIndent(),
    )
    assertTrue(result.isSuccess, result.diagnostics.toString())

    assertDiagnostic(
      analyze(
        "Main.java" to "class A{} class B{} class Main { static int f(A a){return 1;} static int f(B b){return 2;} static int run(){return f(null);} }",
      ),
      "java.semantic.ambiguous_overload",
    )
  }

  /** primitive overload 必须逐参数判 most-specific，不能用 widening 距离总和打破交叉优势。 */
  @Test
  fun rejectsCrossSpecificPrimitiveOverloads() {
    assertDiagnostic(
      analyze(
        "Main.java" to """
          class Main {
            static int pick(short left, int right) { return 1; }
            static int pick(int left, byte right) { return 2; }
            static int run(byte left, byte right) { return pick(left, right); }
          }
        """.trimIndent(),
      ),
      "java.semantic.ambiguous_overload",
    )

    val dominating = analyze(
      "Main.java" to """
        class Main {
          static int pick(short left, int right) { return 1; }
          static int pick(int left, int right) { return 2; }
          static int run(byte left, byte right) { return pick(left, right); }
        }
      """.trimIndent(),
    )
    assertTrue(dominating.isSuccess, dominating.diagnostics.toString())
  }

  /** class/method 泛型、继承代换与 diamond 构造推断共同工作。 */
  @Test
  fun supportsCommonGenericSubstitutionAndInference() {
    val result = analyze(
      "Main.java" to """
        class Box<T> { T value; Box(T value) { this.value = value; } T get() { return value; } }
        class StringBox extends Box<String> { StringBox(String value) { super(value); } }
        class Main {
          static <T> T id(T value) { return value; }
          static String run() {
            Box<String> box = new Box<>(id("ok"));
            StringBox child = new StringBox(box.get());
            return Main.<String>id(child.get());
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = result.value!!
    assertTrue(model.selectedCallables.values.any { it.substitutions.isNotEmpty() })
    assertTrue(model.valueAccesses.values.any { it.symbol in model.fieldDeclarations })
  }

  /** 方法类型参数上界需要先应用 receiver owner 的固定类型代换。 */
  @Test
  fun substitutesOwnerTypeInsideMethodTypeParameterBound() {
    val result = analyze(
      "Main.java" to """
        class C<T> { <U extends T> U id(U value) { return value; } }
        class Main {
          static String run(C<Object> value) {
            String inferred = value.id("ok");
            return value.<String>id(inferred);
          }
        }
      """.trimIndent(),
    )
    assertTrue(result.isSuccess, result.diagnostics.toString())

    assertDiagnostic(
      analyze(
        "Main.java" to "class C<T>{ <U extends T> U id(U x){return x;} } " +
          "class Main{ static Object bad(C<String> c){ return c.<Object>id(new Object()); } }",
      ),
      "java.semantic.no_applicable_overload",
    )
  }

  /** blank final 字段在所有分支各赋一次才 DA；缺支或顺序二次赋值分别失败。 */
  @Test
  fun tracksBlankFinalFieldDefiniteAssignmentAndUnassignment() {
    val success = analyze(
      "Main.java" to """
        class Main {
          final int value;
          Main(boolean flag) {
            if (flag) { this.value = 1; } else { this.value = 2; }
          }
        }
      """.trimIndent(),
    )
    assertTrue(success.isSuccess, success.diagnostics.toString())

    assertDiagnostic(
      analyze(
        "Main.java" to "class Main { final int value; " +
          "Main(boolean flag) { if (flag) { this.value = 1; } } }",
      ),
      "java.semantic.blank_final_field_not_initialized",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to "class Main { final int value; " +
          "Main() { this.value = 1; this.value = 2; } }",
      ),
      "java.semantic.final_assignment",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to "class Main { final int value; " +
          "Main() { while (true) { this.value = 1; } } }",
      ),
      "java.semantic.final_assignment",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to "class Main { final int value; " +
          "Main() { for (int i = 0; true; i++) { this.value = 1; } } }",
      ),
      "java.semantic.final_assignment",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static final int value; }"),
      "java.semantic.blank_final_field_not_initialized",
    )
  }

  /** invariant 与 extends/super wildcard 使用冻结类型关系；擦除冲突稳定拒绝。 */
  @Test
  fun validatesGenericBoundsWildcardsAndErasure() {
    val wildcard = analyze(
      "Main.java" to """
        class Box<T> { }
        class Main {
          static void use() {
            Box<String> strings = null;
            Box<? extends Object> readers = strings;
            Box<? super String> writers = strings;
          }
        }
      """.trimIndent(),
    )
    assertTrue(wildcard.isSuccess, wildcard.diagnostics.toString())

    assertDiagnostic(
      analyze("Main.java" to "class Box<T>{} class Main { static void use(){ Box<String> a=null; Box<Object> b=a; } }"),
      "java.semantic.type_mismatch",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Box<T>{} class Main { static <T> void f(T x){} static void f(Object x){} }"),
      "java.semantic.duplicate_method_descriptor",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to "class Base{} class Box<T extends Base>{} class Main { Box<String> invalid; }",
      ),
      "java.semantic.type_argument_bound",
    )
  }

  /** 继承环、final 父类和跨 package 不可见父类均在签名遍次失败。 */
  @Test
  fun rejectsInvalidInheritance() {
    assertDiagnostic(
      analyze("Main.java" to "class A extends B {} class B extends A {}"),
      "java.semantic.inheritance_cycle",
    )
    assertDiagnostic(
      analyze("Main.java" to "final class A {} class B extends A {}"),
      "java.semantic.extends_final_type",
    )
    assertDiagnostic(
      analyze(
        "a/Hidden.java" to "package a; class Hidden {}",
        "b/Child.java" to "package b; import a.Hidden; class Child extends Hidden {}",
      ),
      "java.semantic.inaccessible_type",
    )
  }

  /** 解析源码并确认前端成功，避免语义断言误吞 CST 失败。 */
  private fun analyze(vararg sources: Pair<String, String>): JavaCompilerPhaseResult<JavaSemanticModel> {
    val workspace = JavaSourceWorkspace(
      sources.mapIndexed { index, (path, source) ->
        JavaSourceFile(JavaSourceFileId(index), path, source)
      },
    )
    val frontend = JavaLezerAstFrontend.parse(workspace)
    assertTrue(frontend.isSuccess, frontend.diagnostics.toString())
    return JavaSemanticAnalyzerImpl.analyze(assertNotNull(frontend.value))
  }

  /** 断言失败结果包含指定稳定诊断 code。 */
  private fun assertDiagnostic(result: JavaCompilerPhaseResult<JavaSemanticModel>, code: String) {
    assertFalse(result.isSuccess, "Expected failure but got a semantic model.")
    assertTrue(
      result.diagnostics.any { it.code == code },
      "Expected $code, actual diagnostics: ${result.diagnostics}",
    )
  }
}
