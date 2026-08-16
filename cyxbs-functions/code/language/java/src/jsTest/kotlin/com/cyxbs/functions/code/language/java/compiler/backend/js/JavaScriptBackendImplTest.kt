package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.java.compiler.JavaCompilerEntryPoint
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrBinaryOperator
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClass
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClassId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstant
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConversion
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrDispatchKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrExpression
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocal
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocalId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethod
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrProgram
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrStatement
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
