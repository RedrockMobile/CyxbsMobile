package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.java.compiler.JavaCompilerEntryPoint
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrBinaryOperator
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClass
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClassId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstant
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstructorInvocationKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConversion
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrDispatchKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrExpression
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrField
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrFieldId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocal
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocalId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethod
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrProgram
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrStatement
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrStringConcatPart
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrStringConversionKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrType
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证阶段 0 typed IR 到 ES Module 的稳定输出和拒绝边界。 */
class JavaScriptBackendImplTest {
  /** while、局部变量、比较、int 算术和入口导出必须产生稳定的单模块快照。 */
  @Test
  fun emitsStage0ProgramSnapshot() {
    val result = JavaScriptBackendImpl.generate(sumProgram(), sumEntryPoint())

    val artifact = assertNotNull(result.value)
    assertEquals(JavaModuleLayout.ENTRY_MODULE_NAME, artifact.entryModuleName)
    assertEquals(JavaModuleLayout.ENTRY_EXPORT_NAME, artifact.entryExportName)
    assertEquals(1, artifact.modules.size)
    assertEquals(
      """
        function @__j_int_div(left, right) {
          left |= 0;
          right |= 0;
          if (right === 0) {
            throw new Error("java.lang.ArithmeticException: / by zero");
          }
          return (left / right) | 0;
        }

        function @__j_int_rem(left, right) {
          left |= 0;
          right |= 0;
          if (right === 0) {
            throw new Error("java.lang.ArithmeticException: / by zero");
          }
          return (left - @__j_int_div(left, right) * right) | 0;
        }

        function @m_10(@l_1) {
          @l_1 = @l_1 | 0;
          let @l_2 = (0 | 0);
          while ((@l_1 > 0)) {
            (@l_2 = (((@l_2 + @l_1) | 0) | 0));
            (@l_1 = (((@l_1 - 1) | 0) | 0));
          }
          return @l_2;
        }

        export function __cyxbs_java_entry__(...args) {
          return @m_10(...args);
        }
      """.trimIndent().replace('@', '$') + "\n",
      artifact.modules.single().source,
    )
    assertEquals(5, artifact.modules.single().sourceMappings.size)
  }

  /** Java 源码名称无论是否是 JS 关键字，都不能改变以 IR id 为准的生成名称。 */
  @Test
  fun ignoresSourceNameHintsWhenMangling() {
    val program = sumProgram(
      parameterName = "class",
      localName = "default",
    )

    val artifact = assertNotNull(JavaScriptBackendImpl.generate(program, sumEntryPoint()).value).modules.single()

    assertTrue("function \$m_10(\$l_1)" in artifact.source)
    assertTrue("let \$l_2" in artifact.source)
    assertTrue("class" !in artifact.source)
    assertTrue("default" !in artifact.source)
  }

  /** static 调用和明确 primitive widening 均必须经过 typed IR，而不是按 Java 名称猜测。 */
  @Test
  fun emitsStaticCallAndIntegralConversion() {
    val artifact = assertNotNull(
      JavaScriptBackendImpl.generate(staticCallProgram(), staticCallEntryPoint()).value,
    ).modules.single()

    assertTrue("function \$m_11(\$l_3)" in artifact.source)
    assertTrue("return \$m_11((\$l_1 | 0));" in artifact.source)
  }

  /** long 当前没有 BigInt runtime 时必须返回结构化诊断，不能输出错误 JavaScript。 */
  @Test
  fun rejectsUnsupportedLongIrWithoutArtifact() {
    val result = JavaScriptBackendImpl.generate(longProgram(), longEntryPoint())

    assertNull(result.value)
    assertTrue(result.diagnostics.any { diagnostic ->
      diagnostic.code == "JAVA_BACKEND_UNSUPPORTED" &&
        diagnostic.message.contains("long")
    })
  }

  /** 对象创建、实例字段默认初始化和 this-free getter 必须在真实 JavaScript 中返回 Java int。 */
  @Test
  fun executesInstanceFieldInitialization() {
    val artifact = assertNotNull(JavaScriptBackendImpl.generate(objectProgram(), objectEntryPoint()).value).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + "\nreturn " + JavaModuleLayout.ENTRY_EXPORT_NAME + ";"
    val constructor: dynamic = js("Function")
    val entry: dynamic = constructor(executable)()

    assertEquals(7, (entry() as Number).toInt())
  }

  /** 静态前向读取、父类优先壳和分配默认值必须在生成源码中彼此独立。 */
  @Test
  fun emitsJavaInitializationOrderingGuards() {
    val artifact = assertNotNull(
      JavaScriptBackendImpl.generate(initializationOrderingProgram(), objectEntryPoint()).value,
    ).modules.single()
    val source = artifact.source

    // child id 小于 parent id 时，prototype 和默认值函数仍先依赖已声明的 parent。
    assertTrue(source.indexOf("const \$c_9") < source.indexOf("const \$c_2"))
    assertTrue(source.indexOf("Object.create(\$p_9)") > source.indexOf("const \$p_9"))
    // static 字段的默认值在 state=1 前写入，显式 initializer 才保留在 clinit 内。
    assertTrue(source.indexOf("\$c_1.values[\"\$f_11\"] = 0;") < source.indexOf("function \$i_1()"))
    assertTrue("function \$d_2(self)" in source)
    assertTrue("\$d_9(self);" in source)
    assertTrue("\$d_2(value);" in source)
    // receiver、右值/实参先进入 IIFE 参数，空检查位于函数体内。
    assertTrue("__j_non_null(receiver)" in source)
    assertTrue("((receiver, values) => \$m_" in source)
  }

  /** 构造器委托和 virtual slot 的错误 IR 必须稳定地在 emitter 前拒绝。 */
  @Test
  fun rejectsIllegalConstructorDelegationAndVirtualSlots() {
    val result = JavaScriptBackendImpl.generate(invalidConstructorAndSlotProgram(), objectEntryPoint())

    assertNull(result.value)
    assertTrue(result.diagnostics.any { it.message.contains("constructor invocation must be the constructor body's direct first statement") })
    assertTrue(result.diagnostics.any { it.message.contains("duplicate virtual slot 7") })
    assertTrue(result.diagnostics.any { it.message.contains("override parameters do not match") })
  }

  /** root this() 链只能在终点构造器执行一次本类初始化，不能在调用方重复执行。 */
  @Test
  fun initializesRootThisDelegationOnlyAtTerminalConstructor() {
    val source = assertNotNull(
      JavaScriptBackendImpl.generate(rootThisDelegationProgram(), objectEntryPoint()).value,
    ).modules.single().source

    assertEquals(1, source.split("\$n_1(this);").size - 1)
  }

  /** 同类 static 字段的前向读取必须得到 Java 默认值而不是 JavaScript undefined。 */
  @Test
  fun executesSameClassStaticForwardInitialization() {
    val artifact = assertNotNull(
      JavaScriptBackendImpl.generate(staticForwardInitializationProgram(), objectEntryPoint()).value,
    ).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + "\nreturn " + JavaModuleLayout.ENTRY_EXPORT_NAME + ";"
    val constructor: dynamic = js("Function")
    val entry: dynamic = constructor(executable)()

    assertEquals(0, (entry() as Number).toInt())
  }

  /** 父构造器虚调子类 override 时，子类字段已在调用任意构造器前按默认值可见。 */
  @Test
  fun exposesChildDefaultFieldDuringParentConstructorVirtualDispatch() {
    val artifact = assertNotNull(
      JavaScriptBackendImpl.generate(parentVirtualDispatchProgram(), objectEntryPoint()).value,
    ).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + "\nreturn " + JavaModuleLayout.ENTRY_EXPORT_NAME + ";"
    val constructor: dynamic = js("Function")
    val entry: dynamic = constructor(executable)()

    assertEquals(0, (entry() as Number).toInt())
  }

  /** invokestatic 与 putstatic 都必须在参数或右值副作用完成后才初始化目标类。 */
  @Test
  fun evaluatesStaticArgumentsAndRightHandSidesBeforeClassInitialization() {
    val artifact = assertNotNull(
      JavaScriptBackendImpl.generate(staticInitializationOrderingProgram(), objectEntryPoint()).value,
    ).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + "\nreturn " + JavaModuleLayout.ENTRY_EXPORT_NAME + ";"
    val constructor: dynamic = js("Function")
    val entry: dynamic = constructor(executable)()

    // read() 依次将 counter 设为 1/2；A、B 的 clinit 必须分别观察到这两个值。
    assertEquals(12, (entry() as Number).toInt())
  }

  /** 协变返回的 override 必须共用祖先虚槽，并仍通过父类型 selected method 派发到子类实现。 */
  @Test
  fun dispatchesCovariantReturnOverrideThroughParentVirtualSlot() {
    val artifact = assertNotNull(
      JavaScriptBackendImpl.generate(covariantVirtualDispatchProgram(), objectEntryPoint()).value,
    ).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + "\nreturn " + JavaModuleLayout.ENTRY_EXPORT_NAME + ";"
    val constructor: dynamic = js("Function")
    val entry: dynamic = constructor(executable)()

    assertEquals(7, (entry() as Number).toInt())
  }

  /** 一维数组必须预填 Java 默认值，并在读写处经统一的边界检查 runtime。 */
  @Test
  fun executesArrayDefaultValuesAndCheckedWrites() {
    val artifact = assertNotNull(
      JavaScriptBackendImpl.generate(arrayProgram(), objectEntryPoint()).value,
    ).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + "\nreturn " + JavaModuleLayout.ENTRY_EXPORT_NAME + ";"
    val constructor: dynamic = js("Function")
    val entry: dynamic = constructor(executable)()

    assertEquals(9, (entry() as Number).toInt())
    // IIFE 的三个参数将 array/index/RHS 固定为各一次求值，再进入 checked store。
    assertTrue("((array, index, value) => ${'$'}__j_array_set(array, index, value))" in artifact.source)
    assertTrue("java.lang.NegativeArraySizeException" in artifact.source)
    assertTrue("java.lang.ArrayIndexOutOfBoundsException" in artifact.source)
  }

  /** 引用 component token 必须阻止 string 绕过用户类数组，也阻止对象写入 String 数组。 */
  @Test
  fun rejectsInvalidReferenceArrayStoresAtRuntime() {
    val artifact = assertNotNull(JavaScriptBackendImpl.generate(arrayProgram(), objectEntryPoint()).value).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + """
      return () => {
        const userClassArray = ${'$'}__j_new_array(1, null, {});
        const stringArray = ${'$'}__j_new_array(1, null, "string");
        try { ${'$'}__j_array_set(userClassArray, 0, "bad"); return false; }
        catch (error) { if (!String(error).includes("java.lang.ArrayStoreException")) return false; }
        try { ${'$'}__j_array_set(stringArray, 0, {}); return false; }
        catch (error) { return String(error).includes("java.lang.ArrayStoreException"); }
      };
    """.trimIndent()
    val constructor: dynamic = js("Function")
    val checks: dynamic = constructor(executable)()

    assertEquals(true, checks() as Boolean)
  }

  /** primitive store 的最终写回必须在 array runtime 内完成 Java 窄化。 */
  @Test
  fun narrowsPrimitiveArrayWritesAtRuntime() {
    val artifact = assertNotNull(JavaScriptBackendImpl.generate(arrayProgram(), objectEntryPoint()).value).modules.single()
    val executable = artifact.source.replace(
      "export function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
      "function " + JavaModuleLayout.ENTRY_EXPORT_NAME,
    ) + """
      return () => {
        const bytes = ${'$'}__j_new_array(1, 0, "primitive:BYTE");
        const chars = ${'$'}__j_new_array(1, 0, "primitive:CHAR");
        ${'$'}__j_array_set(bytes, 0, 128);
        ${'$'}__j_array_set(chars, 0, -1);
        return bytes[0] === -128 && chars[0] === 65535;
      };
    """.trimIndent()
    val constructor: dynamic = js("Function")
    val checks: dynamic = constructor(executable)()

    assertEquals(true, checks() as Boolean)
  }

  /** StringConcat 使用显式 conversion kind，不能退化为 JavaScript 动态加法。 */
  @Test
  fun emitsExplicitStringConcatenationRuntime() {
    val source = assertNotNull(
      JavaScriptBackendImpl.generate(stringConcatProgram(), stringEntryPoint()).value,
    ).modules.single().source

    assertTrue("function ${'$'}__j_string_concat" in source)
    assertTrue("[\"STRING\", \"CHAR\"]" in source)
  }

  /** 构造包含阶段 0 完整控制流的 sum typed IR。 */
  private fun sumProgram(
    parameterName: String = "limit",
    localName: String = "result",
  ): JavaIrProgram {
    val limit = local(1, parameterName, isParameter = true)
    val result = local(2, localName)
    val limitRead = get(limit)
    return JavaIrProgram(
      classes = listOf(
        clazz(
          methods = listOf(
            method(
              id = 10,
              name = "sum",
              descriptor = "(I)I",
              parameters = listOf(limit),
              locals = listOf(result),
              body = JavaIrStatement.Block(
                statements = listOf(
                  JavaIrStatement.DeclareLocal(
                    local = result.id,
                    initializer = intConstant(0),
                    span = span(10),
                  ),
                  JavaIrStatement.While(
                    condition = JavaIrExpression.Binary(
                      left = limitRead,
                      operator = JavaIrBinaryOperator.GREATER_THAN,
                      right = intConstant(0),
                      type = booleanType,
                      span = span(20),
                    ),
                    body = JavaIrStatement.Block(
                      statements = listOf(
                        JavaIrStatement.Expression(
                          expression = set(
                            result,
                            JavaIrExpression.Binary(
                              left = get(result),
                              operator = JavaIrBinaryOperator.ADD,
                              right = get(limit),
                              type = intType,
                              span = span(30),
                            ),
                          ),
                          span = span(31),
                        ),
                        JavaIrStatement.Expression(
                          expression = set(
                            limit,
                            JavaIrExpression.Binary(
                              left = get(limit),
                              operator = JavaIrBinaryOperator.SUBTRACT,
                              right = intConstant(1),
                              type = intType,
                              span = span(40),
                            ),
                          ),
                          span = span(41),
                        ),
                      ),
                      span = span(25),
                    ),
                    span = span(20),
                  ),
                  JavaIrStatement.Return(get(result), span(50)),
                ),
                span = span(1),
              ),
            ),
          ),
        ),
      ),
    )
  }

  /** 构造一次 static invoke 与 primitive widening 的最小 typed IR。 */
  private fun staticCallProgram(): JavaIrProgram {
    val entryParameter = local(1, "input", isParameter = true)
    val helperParameter = local(3, "value", isParameter = true)
    return JavaIrProgram(
      classes = listOf(
        clazz(
          methods = listOf(
            method(
              id = 10,
              name = "entry",
              descriptor = "(I)I",
              parameters = listOf(entryParameter),
              body = JavaIrStatement.Block(
                statements = listOf(
                  JavaIrStatement.Return(
                    expression = JavaIrExpression.InvokeStatic(
                      method = JavaIrMethodId(11),
                      arguments = listOf(
                        JavaIrExpression.Convert(
                          conversion = JavaIrConversion.PrimitiveWidening(
                            from = JavaAstPrimitiveType.BYTE,
                            to = JavaAstPrimitiveType.INT,
                          ),
                          expression = get(entryParameter),
                          type = intType,
                          span = span(61),
                        ),
                      ),
                      type = intType,
                      span = span(62),
                    ),
                    span = span(63),
                  ),
                ),
                span = span(60),
              ),
            ),
            method(
              id = 11,
              name = "identity",
              descriptor = "(I)I",
              parameters = listOf(helperParameter),
              body = JavaIrStatement.Block(
                statements = listOf(JavaIrStatement.Return(get(helperParameter), span(70))),
                span = span(69),
              ),
            ),
          ),
        ),
      ),
    )
  }

  /** 构造一个含 long 常量的 IR，用于锁定当前后端的明确拒绝策略。 */
  private fun longProgram(): JavaIrProgram {
    return JavaIrProgram(
      classes = listOf(
        clazz(
          methods = listOf(
            method(
              id = 10,
              name = "longValue",
              descriptor = "()J",
              returnType = longType,
              body = JavaIrStatement.Block(
                statements = listOf(
                  JavaIrStatement.Return(
                    expression = JavaIrExpression.Constant(
                      value = JavaIrConstant.LongValue("1"),
                      type = longType,
                      span = span(81),
                    ),
                    span = span(81),
                  ),
                ),
                span = span(80),
              ),
            ),
          ),
        ),
      ),
    )
  }

  /** 构造一个根类字段与 static 入口交叉的最小对象 IR，锁定 root constructor 初始化规则。 */
  private fun objectProgram(): JavaIrProgram {
    val objectClass = JavaIrClassId(2)
    val objectField = JavaIrField(
      id = JavaIrFieldId(20), owner = objectClass, name = "value", type = intType,
      isStatic = false, initializer = intConstant(7), span = span(90),
    )
    val constructor = JavaIrMethod(
      id = JavaIrMethodId(12), owner = objectClass, name = "Box", descriptor = "()V",
      dispatch = JavaIrDispatchKind.SPECIAL, virtualSlot = null, returnType = JavaIrType.Void,
      parameters = emptyList(), locals = emptyList(),
      body = JavaIrStatement.Block(emptyList(), span(91)), span = span(91),
      kind = JavaIrMethodKind.CONSTRUCTOR,
    )
    val created = JavaIrExpression.NewObject(
      classId = objectClass, constructor = constructor.id, arguments = emptyList(),
      type = JavaIrType.Reference(objectClass), span = span(92),
    )
    val entry = JavaIrMethod(
      id = JavaIrMethodId(10), owner = classId, name = "entry", descriptor = "()I",
      dispatch = JavaIrDispatchKind.STATIC, virtualSlot = null, returnType = intType,
      parameters = emptyList(), locals = emptyList(),
      body = JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(
          JavaIrExpression.GetField(created, objectField.id, intType, span(93)), span(93),
        )),
        span(94),
      ),
      span = span(94),
    )
    return JavaIrProgram(listOf(
      JavaIrClass(classId, "sample.Main", null, emptyList(), emptyList(), listOf(entry), null, span()),
      JavaIrClass(objectClass, "sample.Box", null, emptyList(), listOf(objectField), listOf(constructor), null, span()),
    ))
  }

  /** 覆盖静态默认值、继承 ID 逆序、构造时字段预填充和 IIFE 求值顺序的最小 IR。 */
  private fun initializationOrderingProgram(): JavaIrProgram {
    val parent = JavaIrClassId(9)
    val child = JavaIrClassId(2)
    val staticForward = JavaIrField(JavaIrFieldId(11), classId, "forward", intType, true, null, span(111))
    val parentField = JavaIrField(JavaIrFieldId(12), parent, "parent", intType, false, null, span(112))
    val childField = JavaIrField(JavaIrFieldId(13), child, "child", intType, false, null, span(113))
    val parentConstructor = JavaIrMethod(
      JavaIrMethodId(21), parent, "Parent", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(emptyList(), span(114)), span(114),
      JavaIrMethodKind.CONSTRUCTOR,
    )
    val childConstructor = JavaIrMethod(
      JavaIrMethodId(22), child, "Child", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.ConstructorInvocation(JavaIrConstructorInvocationKind.SUPER, parentConstructor.id, emptyList(), span(115))),
        span(115),
      ), span(115), JavaIrMethodKind.CONSTRUCTOR,
    )
    val helper = JavaIrMethod(
      JavaIrMethodId(23), classId, "helper", "(I)I", JavaIrDispatchKind.SPECIAL, null,
      intType, listOf(local(24, "value", true)), emptyList(),
      JavaIrStatement.Block(listOf(JavaIrStatement.Return(get(local(24, "value", true)), span(116))), span(116)), span(116),
    )
    val created = JavaIrExpression.NewObject(child, childConstructor.id, emptyList(), JavaIrType.Reference(child), span(117))
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(
          JavaIrStatement.Expression(
            JavaIrExpression.SetField(created, childField.id, intConstant(1), intType, span(118)), span(118),
          ),
          JavaIrStatement.Return(
            JavaIrExpression.InvokeSpecial(created, helper.id, listOf(intConstant(2)), intType, span(119)), span(119),
          ),
        ), span(117),
      ), span(117),
    )
    return JavaIrProgram(listOf(
      JavaIrClass(child, "sample.Child", parent, emptyList(), listOf(childField), listOf(childConstructor), null, span(120)),
      JavaIrClass(classId, "sample.Main", null, emptyList(), listOf(staticForward), listOf(entry, helper), null, span(121)),
      JavaIrClass(parent, "sample.Parent", null, emptyList(), listOf(parentField), listOf(parentConstructor), null, span(122)),
    ))
  }

  /** 构造非首句委托与同 owner slot 重复，用于锁定 validator 的稳定错误边界。 */
  private fun invalidConstructorAndSlotProgram(): JavaIrProgram {
    val parent = JavaIrClassId(8)
    val child = JavaIrClassId(7)
    val constructor = JavaIrMethod(
      JavaIrMethodId(31), classId, "Main", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(
          JavaIrStatement.Return(null, span(131)),
          JavaIrStatement.ConstructorInvocation(JavaIrConstructorInvocationKind.THIS, JavaIrMethodId(31), emptyList(), span(132)),
        ), span(130),
      ), span(130), JavaIrMethodKind.CONSTRUCTOR,
    )
    fun virtual(id: Int): JavaIrMethod = JavaIrMethod(
      JavaIrMethodId(id), classId, "virtual$id", "()I", JavaIrDispatchKind.VIRTUAL, 7,
      intType, emptyList(), emptyList(), JavaIrStatement.Block(listOf(JavaIrStatement.Return(intConstant(id), span(id))), span(id)), span(id),
    )
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(listOf(JavaIrStatement.Return(intConstant(0), span(133))), span(133)), span(133),
    )
    val parentVirtual = JavaIrMethod(
      JavaIrMethodId(34), parent, "slot", "(I)I", JavaIrDispatchKind.VIRTUAL, 8,
      intType, listOf(local(37, "value", true)), emptyList(), JavaIrStatement.Block(listOf(JavaIrStatement.Return(intConstant(0), span(135))), span(135)), span(135),
    )
    val childVirtual = JavaIrMethod(
      JavaIrMethodId(35), child, "slot", "(Z)I", JavaIrDispatchKind.VIRTUAL, 8,
      intType, listOf(local(36, "value", true)), emptyList(), JavaIrStatement.Block(listOf(JavaIrStatement.Return(intConstant(0), span(136))), span(136)), span(136),
    )
    return JavaIrProgram(listOf(
      JavaIrClass(classId, "sample.Main", null, emptyList(), emptyList(), listOf(entry, constructor, virtual(32), virtual(33)), null, span(134)),
      JavaIrClass(child, "sample.Child", parent, emptyList(), emptyList(), listOf(childVirtual), null, span(137)),
      JavaIrClass(parent, "sample.Parent", null, emptyList(), emptyList(), listOf(parentVirtual), null, span(138)),
    ))
  }

  /** 构造根类的 this() 委托链，终点构造器没有显式 constructor invocation。 */
  private fun rootThisDelegationProgram(): JavaIrProgram {
    val field = JavaIrField(JavaIrFieldId(41), classId, "value", intType, false, intConstant(1), span(141))
    val terminal = JavaIrMethod(
      JavaIrMethodId(42), classId, "Main", "(I)V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, listOf(local(43, "unused", true)), emptyList(),
      JavaIrStatement.Block(emptyList(), span(142)), span(142), JavaIrMethodKind.CONSTRUCTOR,
    )
    val delegating = JavaIrMethod(
      JavaIrMethodId(44), classId, "Main", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.ConstructorInvocation(JavaIrConstructorInvocationKind.THIS, terminal.id, listOf(intConstant(0)), span(143))),
        span(143),
      ), span(143), JavaIrMethodKind.CONSTRUCTOR,
    )
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(listOf(JavaIrStatement.Return(intConstant(0), span(144))), span(144)), span(144),
    )
    return JavaIrProgram(listOf(JavaIrClass(
      classId, "sample.Main", null, emptyList(), listOf(field), listOf(entry, terminal, delegating), null, span(145),
    )))
  }

  /** 构造 a 读取后声明 b 的同类 static 初始化，锁定默认值在 clinit 前可见。 */
  private fun staticForwardInitializationProgram(): JavaIrProgram {
    val later = JavaIrField(JavaIrFieldId(51), classId, "later", intType, true, intConstant(7), span(151))
    val earlier = JavaIrField(
      JavaIrFieldId(52), classId, "earlier", intType, true,
      JavaIrExpression.GetStaticField(later.id, intType, span(152)), span(152),
    )
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(JavaIrExpression.GetStaticField(earlier.id, intType, span(153)), span(153))),
        span(153),
      ), span(153),
    )
    return JavaIrProgram(listOf(JavaIrClass(
      classId, "sample.Main", null, emptyList(), listOf(earlier, later), listOf(entry), null, span(154),
    )))
  }

  /** 构造父构造器中的 virtual dispatch，用主类 static 字段观测 child 默认字段。 */
  private fun parentVirtualDispatchProgram(): JavaIrProgram {
    val parent = JavaIrClassId(9)
    val child = JavaIrClassId(2)
    val observed = JavaIrField(JavaIrFieldId(61), classId, "observed", intType, true, null, span(161))
    val childField = JavaIrField(JavaIrFieldId(62), child, "value", intType, false, null, span(162))
    val parentThis = JavaIrExpression.This(JavaIrType.Reference(parent), span(163))
    val virtualBody = JavaIrStatement.Block(
      listOf(JavaIrStatement.Return(
        JavaIrExpression.SetStaticField(
          observed.id,
          JavaIrExpression.GetField(parentThis, childField.id, intType, span(164)),
          intType,
          span(164),
        ),
        span(164),
      )), span(164),
    )
    val parentVirtual = JavaIrMethod(
      JavaIrMethodId(63), parent, "readChild", "()I", JavaIrDispatchKind.VIRTUAL, 4,
      intType, emptyList(), emptyList(), virtualBody, span(163),
    )
    val childVirtual = JavaIrMethod(
      JavaIrMethodId(64), child, "readChild", "()I", JavaIrDispatchKind.VIRTUAL, 4,
      intType, emptyList(), emptyList(), virtualBody, span(165),
    )
    val parentConstructor = JavaIrMethod(
      JavaIrMethodId(65), parent, "Parent", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Expression(
          JavaIrExpression.InvokeVirtual(parentThis, parentVirtual.id, 4, emptyList(), intType, span(166)), span(166),
        )), span(166),
      ), span(166), JavaIrMethodKind.CONSTRUCTOR,
    )
    val childConstructor = JavaIrMethod(
      JavaIrMethodId(67), child, "Child", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.ConstructorInvocation(JavaIrConstructorInvocationKind.SUPER, parentConstructor.id, emptyList(), span(167))),
        span(167),
      ), span(167), JavaIrMethodKind.CONSTRUCTOR,
    )
    val created = JavaIrExpression.NewObject(child, childConstructor.id, emptyList(), JavaIrType.Reference(child), span(168))
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(
          JavaIrStatement.Expression(created, span(168)),
          JavaIrStatement.Return(JavaIrExpression.GetStaticField(observed.id, intType, span(169)), span(169)),
        ), span(168),
      ), span(168),
    )
    return JavaIrProgram(listOf(
      JavaIrClass(classId, "sample.Main", null, emptyList(), listOf(observed), listOf(entry), null, span(170)),
      JavaIrClass(child, "sample.Child", parent, emptyList(), listOf(childField), listOf(childVirtual, childConstructor), null, span(171)),
      JavaIrClass(parent, "sample.Parent", null, emptyList(), emptyList(), listOf(parentVirtual, parentConstructor), null, span(172)),
    ))
  }

  /** 构造 A.f(read()) 与 B.x = read()，以 A/B 的 clinit 记录右值求值时序。 */
  private fun staticInitializationOrderingProgram(): JavaIrProgram {
    val callOwner = JavaIrClassId(2)
    val writeOwner = JavaIrClassId(3)
    val counter = JavaIrField(JavaIrFieldId(71), classId, "counter", intType, true, null, span(181))
    val callObserved = JavaIrField(JavaIrFieldId(72), classId, "callObserved", intType, true, null, span(182))
    val writeObserved = JavaIrField(JavaIrFieldId(73), classId, "writeObserved", intType, true, null, span(183))
    val target = JavaIrField(JavaIrFieldId(74), writeOwner, "x", intType, true, null, span(184))
    val read = JavaIrMethod(
      JavaIrMethodId(75), classId, "read", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(
          JavaIrExpression.SetStaticField(
            counter.id,
            JavaIrExpression.Binary(
              JavaIrExpression.GetStaticField(counter.id, intType, span(185)),
              JavaIrBinaryOperator.ADD,
              intConstant(1),
              intType,
              span(185),
            ),
            intType,
            span(185),
          ),
          span(185),
        )), span(185),
      ), span(185),
    )
    val callParameter = local(76, "value", true)
    val call = JavaIrMethod(
      JavaIrMethodId(77), callOwner, "f", "(I)I", JavaIrDispatchKind.STATIC, null, intType,
      listOf(callParameter), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(get(callParameter), span(186))), span(186),
      ), span(186),
    )
    val callInitializer = JavaIrStatement.Block(
      listOf(JavaIrStatement.Expression(
        JavaIrExpression.SetStaticField(
          callObserved.id,
          JavaIrExpression.GetStaticField(counter.id, intType, span(187)),
          intType,
          span(187),
        ), span(187),
      )), span(187),
    )
    val writeInitializer = JavaIrStatement.Block(
      listOf(JavaIrStatement.Expression(
        JavaIrExpression.SetStaticField(
          writeObserved.id,
          JavaIrExpression.GetStaticField(counter.id, intType, span(188)),
          intType,
          span(188),
        ), span(188),
      )), span(188),
    )
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(
          JavaIrStatement.Expression(
            JavaIrExpression.InvokeStatic(call.id, listOf(JavaIrExpression.InvokeStatic(read.id, emptyList(), intType, span(189))), intType, span(189)),
            span(189),
          ),
          JavaIrStatement.Expression(
            JavaIrExpression.SetStaticField(target.id, JavaIrExpression.InvokeStatic(read.id, emptyList(), intType, span(190)), intType, span(190)),
            span(190),
          ),
          JavaIrStatement.Return(
            JavaIrExpression.Binary(
              JavaIrExpression.Binary(
                JavaIrExpression.GetStaticField(callObserved.id, intType, span(191)),
                JavaIrBinaryOperator.MULTIPLY,
                intConstant(10),
                intType,
                span(191),
              ),
              JavaIrBinaryOperator.ADD,
              JavaIrExpression.GetStaticField(writeObserved.id, intType, span(191)),
              intType,
              span(191),
            ),
            span(191),
          ),
        ), span(189),
      ), span(189),
    )
    return JavaIrProgram(listOf(
      JavaIrClass(classId, "sample.Main", null, emptyList(), listOf(counter, callObserved, writeObserved), listOf(entry, read), null, span(192)),
      JavaIrClass(callOwner, "sample.A", null, emptyList(), emptyList(), listOf(call), callInitializer, span(193)),
      JavaIrClass(writeOwner, "sample.B", null, emptyList(), listOf(target), emptyList(), writeInitializer, span(194)),
    ))
  }

  /** 构造 A.self(): A 与 B.self(): B，通过 A 的 selected method 调用 B override。 */
  private fun covariantVirtualDispatchProgram(): JavaIrProgram {
    val parent = JavaIrClassId(8)
    val child = JavaIrClassId(7)
    val marker = JavaIrField(JavaIrFieldId(81), child, "marker", intType, false, intConstant(7), span(201))
    val parentSelf = JavaIrMethod(
      JavaIrMethodId(82), parent, "self", "()Lsample/A;", JavaIrDispatchKind.VIRTUAL, 9,
      JavaIrType.Reference(parent), emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(JavaIrExpression.This(JavaIrType.Reference(parent), span(202)), span(202))), span(202),
      ), span(202),
    )
    val childSelf = JavaIrMethod(
      JavaIrMethodId(83), child, "self", "()Lsample/B;", JavaIrDispatchKind.VIRTUAL, 9,
      JavaIrType.Reference(child), emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(JavaIrExpression.This(JavaIrType.Reference(child), span(203)), span(203))), span(203),
      ), span(203),
    )
    val parentConstructor = JavaIrMethod(
      JavaIrMethodId(84), parent, "A", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(emptyList(), span(204)), span(204), JavaIrMethodKind.CONSTRUCTOR,
    )
    val childConstructor = JavaIrMethod(
      JavaIrMethodId(85), child, "B", "()V", JavaIrDispatchKind.SPECIAL, null,
      JavaIrType.Void, emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.ConstructorInvocation(JavaIrConstructorInvocationKind.SUPER, parentConstructor.id, emptyList(), span(205))),
        span(205),
      ), span(205), JavaIrMethodKind.CONSTRUCTOR,
    )
    val created = JavaIrExpression.NewObject(child, childConstructor.id, emptyList(), JavaIrType.Reference(child), span(206))
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()I", JavaIrDispatchKind.STATIC, null, intType,
      emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(
          JavaIrExpression.GetField(
            JavaIrExpression.InvokeVirtual(created, parentSelf.id, 9, emptyList(), JavaIrType.Reference(parent), span(207)),
            marker.id,
            intType,
            span(207),
          ),
          span(207),
        )), span(207),
      ), span(207),
    )
    return JavaIrProgram(listOf(
      JavaIrClass(classId, "sample.Main", null, emptyList(), emptyList(), listOf(entry), null, span(208)),
      JavaIrClass(child, "sample.B", parent, emptyList(), listOf(marker), listOf(childSelf, childConstructor), null, span(209)),
      JavaIrClass(parent, "sample.A", null, emptyList(), emptyList(), listOf(parentSelf, parentConstructor), null, span(210)),
    ))
  }

  /** 构造直接可执行的数组 IR，覆盖分配默认值、写回和读取三个 runtime 路径。 */
  private fun arrayProgram(): JavaIrProgram {
    val arrayType = JavaIrType.Array(intType)
    val array = JavaIrLocal(JavaIrLocalId(91), "numbers", arrayType, false, span(91))
    val entry = method(
      id = 10,
      name = "entry",
      descriptor = "()I",
      locals = listOf(array),
      body = JavaIrStatement.Block(
        listOf(
          JavaIrStatement.DeclareLocal(
            array.id,
            JavaIrExpression.NewArray(intType, intConstant(2), arrayType, span(92)),
            span(92),
          ),
          JavaIrStatement.Expression(
            JavaIrExpression.SetArrayElement(
              JavaIrExpression.GetLocal(array.id, arrayType, span(93)), intConstant(1), intConstant(9), intType, span(93),
            ),
            span(93),
          ),
          JavaIrStatement.Return(
            JavaIrExpression.GetArrayElement(
              JavaIrExpression.GetLocal(array.id, arrayType, span(94)), intConstant(1), intType, span(94),
            ),
            span(94),
          ),
        ),
        span(95),
      ),
    )
    return JavaIrProgram(listOf(clazz(listOf(entry))))
  }

  /** 构造一个纯 String 拼接入口，验证 backend 消费 IR conversion kind。 */
  private fun stringConcatProgram(): JavaIrProgram {
    val stringType = JavaIrType.Reference(JavaIrClassId(99))
    val entry = JavaIrMethod(
      JavaIrMethodId(10), classId, "entry", "()Ljava/lang/String;", JavaIrDispatchKind.STATIC, null,
      stringType, emptyList(), emptyList(), JavaIrStatement.Block(
        listOf(JavaIrStatement.Return(
          JavaIrExpression.StringConcat(
            listOf(
              JavaIrStringConcatPart(
                JavaIrExpression.Constant(JavaIrConstant.StringValue("x"), stringType, span(96)),
                JavaIrStringConversionKind.STRING,
              ),
              JavaIrStringConcatPart(intConstant(65), JavaIrStringConversionKind.CHAR),
            ),
            stringType,
            span(96),
          ),
          span(96),
        )),
        span(96),
      ), span(96),
    )
    return JavaIrProgram(listOf(clazz(listOf(entry))))
  }

  /** 创建阶段 0 的单个静态方法，未指定 locals 时保持空集合。 */
  private fun method(
    id: Int,
    name: String,
    descriptor: String,
    parameters: List<JavaIrLocal> = emptyList(),
    locals: List<JavaIrLocal> = emptyList(),
    returnType: JavaIrType = intType,
    body: JavaIrStatement.Block,
  ): JavaIrMethod = JavaIrMethod(
    id = JavaIrMethodId(id),
    owner = classId,
    name = name,
    descriptor = descriptor,
    dispatch = JavaIrDispatchKind.STATIC,
    virtualSlot = null,
    returnType = returnType,
    parameters = parameters,
    locals = locals,
    body = body,
    span = span(id),
  )

  /** 创建无字段、无继承的阶段 0 静态方法容器。 */
  private fun clazz(methods: List<JavaIrMethod>): JavaIrClass = JavaIrClass(
    id = classId,
    qualifiedName = "sample.Main",
    superClass = null,
    interfaces = emptyList(),
    fields = emptyList(),
    methods = methods,
    staticInitializer = null,
    span = span(),
  )

  /** 创建 typed IR 局部变量；ID 而不是 nameHint 决定后端名称。 */
  private fun local(id: Int, nameHint: String, isParameter: Boolean = false): JavaIrLocal = JavaIrLocal(
    id = JavaIrLocalId(id),
    nameHint = nameHint,
    type = intType,
    isParameter = isParameter,
    span = span(id),
  )

  /** 创建一个已确定为 int 的常量表达式。 */
  private fun intConstant(value: Int): JavaIrExpression.Constant = JavaIrExpression.Constant(
    value = JavaIrConstant.IntValue(value),
    type = intType,
    span = span(value + 100),
  )

  /** 创建局部变量读取表达式。 */
  private fun get(local: JavaIrLocal): JavaIrExpression.GetLocal = JavaIrExpression.GetLocal(
    local = local.id,
    type = local.type,
    span = local.span,
  )

  /** 创建 Java assignment expression，保证赋值结果也保持 int 语义。 */
  private fun set(local: JavaIrLocal, value: JavaIrExpression): JavaIrExpression.SetLocal =
    JavaIrExpression.SetLocal(
      local = local.id,
      value = value,
      type = local.type,
      span = value.span,
    )

  /** 生成测试中使用的唯一 Java 静态入口定位。 */
  private fun sumEntryPoint(): JavaCompilerEntryPoint = entryPoint("sum", "(I)I")

  /** 生成 static invoke 测试的入口定位。 */
  private fun staticCallEntryPoint(): JavaCompilerEntryPoint = entryPoint("entry", "(I)I")

  /** 生成 long 拒绝测试的入口定位。 */
  private fun longEntryPoint(): JavaCompilerEntryPoint = entryPoint("longValue", "()J")

  private fun objectEntryPoint(): JavaCompilerEntryPoint = entryPoint("entry", "()I")

  private fun stringEntryPoint(): JavaCompilerEntryPoint = entryPoint("entry", "()Ljava/lang/String;")

  /** 统一构造 Java 编译入口，避免测试意外依赖名称解析。 */
  private fun entryPoint(name: String, descriptor: String): JavaCompilerEntryPoint = JavaCompilerEntryPoint(
    qualifiedClassName = "sample.Main",
    methodName = name,
    descriptor = descriptor,
  )

  /** 创建测试用 UTF-16 源码区间。 */
  private fun span(from: Int = 0): JavaSourceSpan = JavaSourceSpan(
    fileId = JavaSourceFileId(0),
    from = from,
    to = from + 1,
  )

  private companion object {
    val classId = JavaIrClassId(1)
    val intType = JavaIrType.Primitive(JavaAstPrimitiveType.INT)
    val booleanType = JavaIrType.Primitive(JavaAstPrimitiveType.BOOLEAN)
    val longType = JavaIrType.Primitive(JavaAstPrimitiveType.LONG)
  }
}
