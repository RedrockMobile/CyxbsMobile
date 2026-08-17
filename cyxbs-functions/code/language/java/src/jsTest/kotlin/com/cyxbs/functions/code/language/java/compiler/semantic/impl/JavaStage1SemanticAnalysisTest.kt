package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinCompatibility
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinMemberDescriptor
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinOperation
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.frontend.JavaLezerAstFrontend
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaDispatchKind
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaConstructorDelegationKind
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaConstantValue
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticCallableKind
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticConversion
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticModel
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticType
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaStringConversionKind
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

  /** 一维数组创建、初始化、索引、length 与元素更新必须产出完整类型和转换绑定。 */
  @Test
  fun supportsOneDimensionalArrayOperations() {
    val result = analyze(
      "Main.java" to """
        class Box<T> { }
        class Main {
          static Box<?>[] wildcardArray() { return new Box<?>[1]; }
          static int run(byte index) {
            final int[] initialized = new int[]{1, 2};
            int[] sized = new int[2];
            initialized[index] += 2;
            sized[index]++;
            return initialized.length + initialized[index] + sized[index];
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    assertTrue(model.expressionTypes.values.any { it is JavaSemanticType.Array })
    assertEquals(1, model.arrayLengthExpressions.size)
    assertTrue(
      model.conversions.values.any {
        it == JavaSemanticConversion.PrimitiveWidening(
          JavaAstPrimitiveType.BYTE,
          JavaAstPrimitiveType.INT,
        )
      },
    )
    assertTrue(model.expressionTypes.values.none { it == JavaSemanticType.Error })
  }

  /** 数组形态、索引、元素和 length 的越界语义都应产生稳定诊断。 */
  @Test
  fun rejectsUnsupportedOrInvalidArrayOperations() {
    assertDiagnostic(
      analyze("Main.java" to "class Main { static int[] bad() { return new int[1L]; } }"),
      "java.semantic.array_index_type",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to
          "class Main { static int bad(boolean index) { int[] a = new int[1]; return a[index]; } }",
      ),
      "java.semantic.array_index_type",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static int[] bad() { return new int[]{1L}; } }"),
      "java.semantic.array_initializer_type_mismatch",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to
          "class Main { static void bad() { int[] a = new int[1]; a[0] = \"bad\"; } }",
      ),
      "java.semantic.type_mismatch",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to
          "class Main { static void bad() { int[] a = new int[1]; a.length = 2; } }",
      ),
      "java.semantic.invalid_assignment_target",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static int[][] bad() { return new int[1][2]; } }"),
      "java.semantic.multidimensional_array_creation_unsupported",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static int[][] bad() { return new int[][]{{1}}; } }"),
      "java.semantic.nested_array_initializer_unsupported",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main<T> { T[] bad() { return new T[1]; } }"),
      "java.semantic.generic_array_creation_unsupported",
    )
  }

  /** String `+` 和 `+=` 记录逐操作数转换，保证 char/null 等不依赖 JavaScript 动态加法。 */
  @Test
  fun recordsRestrictedStringConcatenationBindings() {
    val result = analyze(
      "Main.java" to """
        class Main {
          static String numbers() { return 1 + 2 + "n"; }
          static String run(boolean flag) {
            String value = "v";
            value += 'A';
            return value + null + flag + 1;
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val bindings = model.stringConcatenations.values
    assertEquals(5, bindings.size)
    assertTrue(model.constants.values.any { it == JavaConstantValue.IntValue('A'.code) })
    assertTrue(bindings.any {
      it.leftKind == JavaStringConversionKind.INT_LIKE &&
        it.rightKind == JavaStringConversionKind.STRING
    })
    assertTrue(bindings.any {
      it.leftKind == JavaStringConversionKind.STRING &&
        it.rightKind == JavaStringConversionKind.CHAR
    })
    assertTrue(bindings.any { it.rightKind == JavaStringConversionKind.NULL })
    assertTrue(bindings.any { it.rightKind == JavaStringConversionKind.BOOLEAN })
    assertTrue(bindings.any { it.rightKind == JavaStringConversionKind.INT_LIKE })
  }

  /** 首批不借用 boxing、Object.toString 或后端长整型/浮点格式化完成 String 拼接。 */
  @Test
  fun rejectsStringConcatenationWithoutFrozenRuntimeConversion() {
    assertDiagnostic(
      analyze(
        "Main.java" to
          "class Main { static String bad(Object value) { return \"v\" + value; } }",
      ),
      "java.semantic.string_concat_operand_unsupported",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to
          "class Main { static String bad() { int[] value = new int[1]; return \"v\" + value; } }",
      ),
      "java.semantic.string_concat_operand_unsupported",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static String bad(long value) { return \"v\" + value; } }"),
      "java.semantic.string_concat_operand_unsupported",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static String bad(double value) { return \"v\" + value; } }"),
      "java.semantic.string_concat_operand_unsupported",
    )
  }

  /** System、PrintStream 与 Math 应复用现有可见性、widening 和调用绑定基础设施。 */
  @Test
  fun bindsBuiltinFieldsPrintOverloadsAndMathCalls() {
    val result = analyze(
      "Main.java" to """
        import java.io.PrintStream;

        class Main {
          static int run(byte small, short medium, boolean flag) {
            PrintStream output = System.out;
            output.print(small);
            System.err.println(medium);
            System.out.print(flag);
            System.out.println('A');
            String nullable = null;
            System.out.print(nullable);
            return Math.max(Math.abs(-1), Math.min(2, 3));
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val builtinTypes = model.typeDeclarations.values
      .filter { it.kind.name == "BUILTIN" }
      .mapTo(mutableSetOf()) { it.qualifiedName }
    assertEquals(
      setOf(
        "java.lang.Object",
        "java.lang.String",
        "java.lang.System",
        "java.lang.Math",
        "java.io.PrintStream",
        "java.lang.Number",
        "java.lang.Boolean",
        "java.lang.Byte",
        "java.lang.Short",
        "java.lang.Character",
        "java.lang.Integer",
        "java.lang.StringBuilder",
        "java.util.List",
        "java.util.ArrayList",
        "java.util.Set",
        "java.util.HashSet",
        "java.util.Map",
        "java.util.HashMap",
        "java.util.Iterator",
        "java.io.InputStream",
        "java.util.Scanner",
      ),
      builtinTypes,
    )
    assertFalse(
      model.typeDeclarations.values.single { it.qualifiedName == "java.io.PrintStream" }.isFinal,
    )
    val operations = model.builtinMembers.values.mapTo(mutableSetOf()) { it.operation }
    assertTrue(JavaBuiltinOperation.SYSTEM_OUT in operations)
    assertTrue(JavaBuiltinOperation.SYSTEM_ERR in operations)
    assertTrue(JavaBuiltinOperation.PRINTSTREAM_PRINT_INT in operations)
    assertTrue(JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR in operations)
    assertTrue(JavaBuiltinOperation.MATH_ABS_INT in operations)
    assertTrue(JavaBuiltinOperation.MATH_MIN_INT in operations)
    assertTrue(JavaBuiltinOperation.MATH_MAX_INT in operations)

    val builtinCalls = model.selectedCallables.values.filter { binding ->
      binding.symbol in model.builtinMembers
    }
    assertEquals(8, builtinCalls.size)
    assertTrue(builtinCalls.any { it.dispatch == JavaDispatchKind.STATIC })
    assertTrue(builtinCalls.filter { binding ->
      val descriptor = model.builtinMembers.getValue(binding.symbol)
      descriptor.ownerQualifiedName == "java.io.PrintStream"
    }.all { it.dispatch == JavaDispatchKind.SPECIAL && it.virtualSlot == null })
    assertTrue(model.conversions.values.any { conversion ->
      conversion == JavaSemanticConversion.PrimitiveWidening(
        JavaAstPrimitiveType.BYTE,
        JavaAstPrimitiveType.INT,
      )
    })
    assertTrue(model.conversions.values.any { conversion ->
      conversion == JavaSemanticConversion.PrimitiveWidening(
        JavaAstPrimitiveType.SHORT,
        JavaAstPrimitiveType.INT,
      )
    })
  }

  /** 静态 String 类型的 null 必须选择 String 输出重载，不能借用 Object 输出。 */
  @Test
  fun selectsStringPrintOverloadForStringTypedNull() {
    val result = analyze(
      "Main.java" to "class Main { static void run() { String value = null; System.out.println(value); } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val call = model.selectedCallables.values.single()
    val member = model.builtinMembers.getValue(call.symbol)
    assertEquals(JavaBuiltinOperation.PRINTSTREAM_PRINTLN_STRING, member.operation)
    assertEquals("(Ljava/lang/String;)V", call.erasedDescriptor)
  }

  /** wrapper loose invocation、运算拆箱与 StringBuilder 构造均由 builtin 绑定驱动。 */
  @Test
  fun bindsWrapperConversionsAndStringBuilderConstruction() {
    val result = analyze(
      "Main.java" to """
        class Main {
          static int pick(int value) { return 1; }
          static int pick(Byte value) { return 2; }
          static int run(byte small) {
            Integer boxed = 1;
            int value = boxed + 2;
            boxed++;
            boxed += 3;
            StringBuilder builder = new StringBuilder("x");
            String nullable = null;
            builder.append(true).append('A').append(value).append(nullable);
            return pick(small) + builder.length() + boxed.intValue();
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    assertTrue(model.conversions.values.any { it is JavaSemanticConversion.Boxing })
    assertTrue(model.conversions.values.any { it is JavaSemanticConversion.Unboxing })
    assertTrue(model.updateWriteConversions.values.any { conversion ->
      conversion is JavaSemanticConversion.Boxing || conversion is JavaSemanticConversion.Sequence
    })
    val selectedOperations = model.selectedCallables.values.mapNotNull { binding ->
      model.builtinMembers[binding.symbol]?.operation
    }
    assertTrue(JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING in selectedOperations)
    assertTrue(JavaBuiltinOperation.STRING_BUILDER_APPEND_BOOLEAN in selectedOperations)
    assertTrue(JavaBuiltinOperation.INTEGER_INT_VALUE in selectedOperations)
    val pick = model.selectedCallables.values.first { binding ->
      model.symbols.getValue(binding.symbol).name == "pick"
    }
    assertEquals("(I)I", pick.erasedDescriptor)
  }

  /** deprecated wrapper 构造器不在 allowlist 中，必须在源码 new 位置稳定拒绝。 */
  @Test
  fun rejectsWrapperConstructorsOutsideAllowlist() {
    val result = analyze(
      "Main.java" to "class Main { static Integer run() { return new Integer(1); } }",
    )

    assertDiagnostic(result, "java.semantic.no_applicable_overload")
  }

  /** 显式单类型 import 必须覆盖隐式 java.lang，避免用户 System 被错误绑定到 builtin。 */
  @Test
  fun prefersExplicitImportOverImplicitJavaLangType() {
    val result = analyze(
      "lesson/System.java" to
        "package lesson; public class System { public static int value = 7; }",
      "app/Main.java" to
        "package app; import lesson.System; class Main { static int run() { return System.value; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val accessedField = model.valueAccesses.values.single().symbol
    val fieldOwner = model.fieldDeclarations.getValue(accessedField).owner
    assertEquals("lesson.System", model.typeDeclarations.getValue(fieldOwner).qualifiedName)
    assertTrue(accessedField !in model.builtinMembers)
  }

  /** 显式 single-type import 必须优先于同包其他文件中的同名类型。 */
  @Test
  fun prefersExplicitImportOverSamePackageType() {
    val result = analyze(
      "p/Math.java" to "package p; class Math { }",
      "p/Main.java" to
        "package p; import java.lang.Math; class Main { static int run() { return Math.max(1, 2); } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val selected = model.selectedCallables.values.single()
    assertEquals(JavaBuiltinOperation.MATH_MAX_INT, model.builtinMembers.getValue(selected.symbol).operation)
  }

  /** 当前 CU 顶层类型与异符号 single-type import 同名时，即使未引用也必须稳定诊断。 */
  @Test
  fun rejectsExplicitImportConflictingWithCurrentUnitType() {
    assertDiagnostic(
      analyze(
        "p/Math.java" to "package p; import java.lang.Math; class Math { }",
      ),
      "java.semantic.import_conflicts_with_top_level_type",
    )
  }

  /** builtin 复用首个 CU 仅用于 span；java.lang CU 的显式 import 仍必须覆盖同名 builtin。 */
  @Test
  fun doesNotTreatBuiltinHostUnitAsCurrentUnitDeclaration() {
    val result = analyze(
      "java/lang/Main.java" to
        "package java.lang; import lesson.Math; class Main { static int run() { return Math.value; } }",
      "lesson/Math.java" to
        "package lesson; public class Math { public static int value = 7; }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val accessedField = model.valueAccesses.values.single().symbol
    val owner = model.fieldDeclarations.getValue(accessedField).owner
    assertEquals("lesson.Math", model.typeDeclarations.getValue(owner).qualifiedName)
    assertTrue(accessedField !in model.builtinMembers)
  }

  /** String 常用成员和 int 范围 Math 方法应按精确参数数量与类型绑定。 */
  @Test
  fun bindsAllowlistedStringAndMathMembers() {
    val result = analyze(
      "Main.java" to """
        class Main {
          static int run(String value) {
            boolean empty = value.isEmpty();
            boolean equal = value.equals(null);
            boolean contains = value.contains("a");
            boolean starts = value.startsWith("a");
            boolean ends = value.endsWith("z");
            String tail = value.substring(1);
            String middle = value.substring(1, 2);
            return value.length() + value.charAt(0) + value.indexOf('a') +
              value.indexOf("a") + Math.abs(-1) + Math.min(1, 2) + Math.max(1, 2);
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val selectedOperations = model.selectedCallables.values.map { binding ->
      model.builtinMembers.getValue(binding.symbol).operation
    }.toSet()
    assertEquals(
      setOf(
        JavaBuiltinOperation.STRING_IS_EMPTY,
        JavaBuiltinOperation.STRING_EQUALS,
        JavaBuiltinOperation.STRING_CONTAINS,
        JavaBuiltinOperation.STRING_STARTS_WITH,
        JavaBuiltinOperation.STRING_ENDS_WITH,
        JavaBuiltinOperation.STRING_SUBSTRING_FROM,
        JavaBuiltinOperation.STRING_SUBSTRING_RANGE,
        JavaBuiltinOperation.STRING_LENGTH,
        JavaBuiltinOperation.STRING_CHAR_AT,
        JavaBuiltinOperation.STRING_INDEX_OF_CHAR,
        JavaBuiltinOperation.STRING_INDEX_OF_STRING,
        JavaBuiltinOperation.MATH_ABS_INT,
        JavaBuiltinOperation.MATH_MIN_INT,
        JavaBuiltinOperation.MATH_MAX_INT,
      ),
      selectedOperations,
    )
    val contains = model.builtinMembers.values.single { member ->
      member.operation == JavaBuiltinOperation.STRING_CONTAINS
    }
    assertEquals(JavaBuiltinCompatibility.RESTRICTED_COMPATIBLE, contains.compatibility)
    assertTrue(contains is JavaBuiltinMemberDescriptor.Callable)
  }

  /** builtin 调用结果作为下一次调用 receiver 时必须继续携带 String 返回类型和独立 binding。 */
  @Test
  fun bindsNestedBuiltinInvocationReceiver() {
    val result = analyze(
      "Main.java" to """
        class Main {
          static int run(String value) {
            if (value.substring(0, 1).equals("a")) {
              return value.substring(0, 1).length();
            }
            return 0;
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val operations = model.selectedCallables.values.map {
      model.builtinMembers.getValue(it.symbol).operation
    }
    assertEquals(2, operations.count { it == JavaBuiltinOperation.STRING_SUBSTRING_RANGE })
    assertTrue(JavaBuiltinOperation.STRING_EQUALS in operations)
    assertTrue(JavaBuiltinOperation.STRING_LENGTH in operations)
  }

  /** 未进入 allowlist 的字段、方法和构造器必须停在语义阶段。 */
  @Test
  fun rejectsMembersOutsideBuiltinAllowlist() {
    assertDiagnostic(
      analyze("Main.java" to "class Main { static Object bad() { return System.console; } }"),
      "java.semantic.unknown_field",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static void bad() { System.out.printf(\"x\"); } }"),
      "java.semantic.no_applicable_overload",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static void bad() { System.out.print(); } }"),
      "java.semantic.no_applicable_overload",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static int bad() { return Math.sqrt(1); } }"),
      "java.semantic.no_applicable_overload",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static Math bad() { return new Math(); } }"),
      "java.semantic.no_applicable_overload",
    )
  }

  /** 集合 facade、继承成员代换和目标类型 diamond 必须在同一语义模型中闭合。 */
  @Test
  fun bindsBuiltinGenericCollectionsAndTargetTypedDiamond() {
    val result = analyze(
      "Main.java" to """
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.HashSet;
        import java.util.Iterator;
        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        class Main {
          static int run() {
            List<Integer> values = new ArrayList<>();
            values.add(1);
            Integer first = values.get(0);
            values.remove(0);
            values.remove(Integer.valueOf(1));
            Set<String> names = new HashSet<>();
            names.add("a");
            Iterator<String> iterator = names.iterator();
            Map<String, Integer> scores = new HashMap<>();
            scores.put("a", first);
            Set<String> keys = scores.keySet();
            if (iterator.hasNext() && keys.contains("a")) return scores.get("a");
            return 0;
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val operations = model.selectedCallables.values.mapNotNull { binding ->
      model.builtinMembers[binding.symbol]?.operation
    }
    assertTrue(JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT in operations)
    assertTrue(JavaBuiltinOperation.HASH_SET_CONSTRUCT in operations)
    assertTrue(JavaBuiltinOperation.HASH_MAP_CONSTRUCT in operations)
    assertTrue(JavaBuiltinOperation.LIST_GET in operations)
    assertTrue(JavaBuiltinOperation.LIST_REMOVE_INDEX in operations)
    assertTrue(JavaBuiltinOperation.LIST_REMOVE_OBJECT in operations)
    assertTrue(JavaBuiltinOperation.MAP_KEY_SET in operations)
  }

  /** Scanner 只能通过 System.in 构造，全部精选方法都必须绑定到受限 builtin operation。 */
  @Test
  fun bindsBuiltinScannerAndRejectsNonInputStreamConstruction() {
    val result = analyze(
      "Main.java" to """
        import java.util.Scanner;
        class Main {
          static int run() {
            Scanner scanner = new Scanner(System.in);
            scanner.hasNext();
            scanner.next();
            scanner.hasNextInt();
            scanner.nextInt();
            scanner.hasNextLine();
            scanner.nextLine();
            return 0;
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val operations = model.selectedCallables.values.mapNotNull { binding ->
      model.builtinMembers[binding.symbol]?.operation
    }.toSet()
    assertTrue(model.valueAccesses.values.any { access ->
      model.builtinMembers[access.symbol]?.operation == JavaBuiltinOperation.SYSTEM_IN
    })
    assertTrue(JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM in operations)
    assertTrue(JavaBuiltinOperation.SCANNER_HAS_NEXT in operations)
    assertTrue(JavaBuiltinOperation.SCANNER_NEXT in operations)
    assertTrue(JavaBuiltinOperation.SCANNER_HAS_NEXT_INT in operations)
    assertTrue(JavaBuiltinOperation.SCANNER_NEXT_INT in operations)
    assertTrue(JavaBuiltinOperation.SCANNER_HAS_NEXT_LINE in operations)
    assertTrue(JavaBuiltinOperation.SCANNER_NEXT_LINE in operations)
    val scannerMembers = model.selectedCallables.values.mapNotNull { binding ->
      model.builtinMembers[binding.symbol]
    }.filter { member -> member.ownerQualifiedName == "java.util.Scanner" }
    assertTrue(scannerMembers.all { it.compatibility == JavaBuiltinCompatibility.RESTRICTED_COMPATIBLE })

    assertDiagnostic(
      analyze(
        "Main.java" to """
          import java.util.Scanner;
          class Main { static void run() { new Scanner(System.out); } }
        """.trimIndent(),
      ),
      "java.semantic.no_applicable_overload",
    )
  }

  /** 调用与构造参数位置先用非 poly 实参筛选 overload，再把唯一 target type 提交给 diamond。 */
  @Test
  fun infersTargetTypedDiamondInCallableArguments() {
    val result = analyze(
      "Main.java" to """
        import java.util.ArrayList;
        import java.util.List;
        class Holder { Holder(List<Integer> values) {} }
        class Main {
          static void take(List<Integer> values) {}
          static <T> void generic(List<T> values, T element) {}
          static int pick(List<String> values, int marker) { return 1; }
          static int pick(List<Integer> values, String marker) { return 2; }
          static int phase(List<String> values, int marker) { return 3; }
          static int phase(List<Integer> values, Integer marker) { return 4; }
          static <T extends Number> int bounded(List<String> values, T marker) { return 5; }
          static int bounded(List<Integer> values, String marker) { return 6; }
          static int run() {
            take(new ArrayList<>());
            new Holder(new ArrayList<>());
            generic(new ArrayList<>(), "x");
            return pick(new ArrayList<>(), "x") + phase(new ArrayList<>(), 1) +
              bounded(new ArrayList<>(), "x");
          }
        }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    val picked = model.selectedCallables.values.filter { binding ->
      model.symbols.getValue(binding.symbol).name in setOf("pick", "phase", "bounded")
    }.associateBy { binding -> model.symbols.getValue(binding.symbol).name }
    assertEquals("(Ljava/util/List;Ljava/lang/String;)I", picked.getValue("pick").erasedDescriptor)
    assertEquals("(Ljava/util/List;I)I", picked.getValue("phase").erasedDescriptor)
    assertEquals("(Ljava/util/List;Ljava/lang/String;)I", picked.getValue("bounded").erasedDescriptor)
  }

  /** 已有非 poly 约束违反类型变量上界时，冲突候选不得为 diamond 提供伪 target。 */
  @Test
  fun rejectsConflictingPartialGenericInferenceForDiamond() {
    assertDiagnostic(
      analyze(
        "Main.java" to """
          import java.util.ArrayList;
          import java.util.List;
          class Main {
            static <T extends Number> void bounded(List<String> values, T marker) {}
            static void run() { bounded(new ArrayList<>(), "x"); }
          }
        """.trimIndent(),
      ),
      "java.semantic.diamond_target_required",
    )
  }

  /** Object 输出既承接内建包装类型，也允许用户对象在运行时走虚方法分派。 */
  @Test
  fun bindsObjectOutputForWrapperAndUserClass() {
    val supported = analyze(
      "Main.java" to """
        class Main {
          static void run() {
            Integer value = null;
            System.out.println(value);
            new StringBuilder().append(value);
          }
        }
      """.trimIndent(),
    )
    assertTrue(supported.isSuccess, supported.diagnostics.toString())
    val model = assertNotNull(supported.value)
    val operations = model.selectedCallables.values.mapNotNull { binding ->
      model.builtinMembers[binding.symbol]?.operation
    }
    assertTrue(JavaBuiltinOperation.PRINTSTREAM_PRINTLN_OBJECT in operations)
    assertTrue(JavaBuiltinOperation.STRING_BUILDER_APPEND_OBJECT in operations)

    val userClass = analyze(
      "Main.java" to """
        class User {
          public String toString() { return "User"; }
        }
        class Main {
          static void run() { System.out.println(new User()); }
        }
      """.trimIndent(),
    )
    assertTrue(userClass.isSuccess, userClass.diagnostics.toString())
    val userModel = assertNotNull(userClass.value)
    assertTrue(userModel.selectedCallables.values.any { binding ->
      userModel.builtinMembers[binding.symbol]?.operation == JavaBuiltinOperation.PRINTSTREAM_PRINTLN_OBJECT
    })
  }

  /** char[] 精确重载输出字符内容；裸 null 在 String 与 char[] 之间必须保持 Java 歧义。 */
  @Test
  fun bindsCharArrayOutputAndPreservesNullAmbiguity() {
    val supported = analyze(
      "Main.java" to """
        class Main {
          static void run() {
            char[] text = {'o', 'k'};
            System.out.print(text);
            System.out.println(text);
            new StringBuilder().append(text);
          }
        }
      """.trimIndent(),
    )
    assertTrue(supported.isSuccess, supported.diagnostics.toString())
    val model = assertNotNull(supported.value)
    val operations = model.selectedCallables.values.mapNotNull { binding ->
      model.builtinMembers[binding.symbol]?.operation
    }
    assertTrue(JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR_ARRAY in operations)
    assertTrue(JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR_ARRAY in operations)
    assertTrue(JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR_ARRAY in operations)

    assertDiagnostic(
      analyze("Main.java" to "class Main { static void run() { System.out.println(null); } }"),
      "java.semantic.ambiguous_overload",
    )
    assertDiagnostic(
      analyze("Main.java" to "class Main { static void run() { new StringBuilder().append(null); } }"),
      "java.semantic.ambiguous_overload",
    )
  }

  /** 泛型错配与缺失目标的零参 diamond 必须在语义阶段稳定拒绝。 */
  @Test
  fun rejectsInvalidBuiltinCollectionGenericUse() {
    assertDiagnostic(
      analyze(
        "Main.java" to """
          import java.util.ArrayList;
          import java.util.List;
          class Main { static void run() { List<String> values = new ArrayList<Integer>(); } }
        """.trimIndent(),
      ),
      "java.semantic.type_mismatch",
    )
    assertDiagnostic(
      analyze(
        "Main.java" to """
          import java.util.ArrayList;
          class Main { static void run() { new ArrayList<>(); } }
        """.trimIndent(),
      ),
      "java.semantic.diamond_target_required",
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
