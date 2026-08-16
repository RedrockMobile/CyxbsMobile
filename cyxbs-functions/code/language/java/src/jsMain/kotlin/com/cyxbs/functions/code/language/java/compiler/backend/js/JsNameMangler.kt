package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClassId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrFieldId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocalId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodId

/**
 * 依据 typed IR 编号生成 JavaScript 内部名称。
 *
 * 不使用 Java 源码名称作为可执行标识，避免关键字、Unicode 逃逸和遮蔽造成生成结果不稳定；
 * 源码名称只保留在上游诊断与调试信息中。
 */
internal object JsNameMangler {
  /** 返回类静态存储对象的确定性名称。 */
  fun staticStorage(classId: JavaIrClassId): String = "\$c_${classId.value}"

  /** 返回静态方法的确定性名称。 */
  fun method(methodId: JavaIrMethodId): String = "\$m_${methodId.value}"

  /** 返回局部变量或参数的确定性名称。 */
  fun local(localId: JavaIrLocalId): String = "\$l_${localId.value}"

  /** 返回字段属性名；使用方必须通过方括号访问，避免属性名不是 JS 标识符。 */
  fun field(fieldId: JavaIrFieldId): String = "\$f_${fieldId.value}"
}
