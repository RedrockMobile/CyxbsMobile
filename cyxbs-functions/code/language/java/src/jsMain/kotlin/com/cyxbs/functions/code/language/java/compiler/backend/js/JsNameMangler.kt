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

  /** 返回实例原型的确定性名称；属性仅由 IR 编号寻址。 */
  fun prototype(classId: JavaIrClassId): String = "\$p_${classId.value}"

  /** 返回类懒初始化函数的确定性名称。 */
  fun classInitializer(classId: JavaIrClassId): String = "\$i_${classId.value}"

  /** 返回本类实例字段初始化函数的确定性名称。 */
  fun instanceInitializer(classId: JavaIrClassId): String = "\$n_${classId.value}"

  /** 返回对象分配时填充整条继承链字段默认值的确定性名称。 */
  fun instanceDefaultInitializer(classId: JavaIrClassId): String = "\$d_${classId.value}"

  /** 返回静态方法的确定性名称。 */
  fun method(methodId: JavaIrMethodId): String = "\$m_${methodId.value}"

  /** 返回局部变量或参数的确定性名称。 */
  fun local(localId: JavaIrLocalId): String = "\$l_${localId.value}"

  /** 返回字段属性名；使用方必须通过方括号访问，避免属性名不是 JS 标识符。 */
  fun field(fieldId: JavaIrFieldId): String = "\$f_${fieldId.value}"

  /** 返回虚分派槽位属性名，override 必须复用同一编号。 */
  fun virtualSlot(slot: Int): String = "\$v_$slot"
}
