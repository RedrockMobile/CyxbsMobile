package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import java.io.Serializable

/**
 *
 * @ProjectName:    CyxbsMobile_Android
 * @Package:        com.cyxbs.pages.noclass.bean
 * @ClassName:      Student
 * @Author:         Yan
 * @CreateDate:     2022年09月02日 00:20:00
 * @UpdateRemark:   更新说明：
 * @Version:        1.0
 * @Description:
 */
/**
 * 要注意以下两点
 * 1：在计算hashmap值的时候会跳过类型为可空并且为空的，但是如果类型是不可空的，由于网络请求没有填装进去，所以为null，此时就会报空指针异常
 * 解决办法：让类型变成可空的，这样hashmap就会在值已空的情况下跳过了
 * 2：textView的text是可以为null的，所以下面你的major也不必担心
 * 3: alternate是可替代的映射，也就是前面的找不到就会往后找名称相同的
 */
@kotlinx.serialization.Serializable
data class Student(
  @SerialName("classnum")
  val classNum: String? = null,
  @SerialName("gender")
  val gender: String? = null,
  @SerialName("grade")
  val grade: String? = null,
  @SerialName("major")
  val major: String? = null,
  @SerialName("depart")
  val depart: String? = null,   //depart是学院

  // 因为 R8 生成了无参构造函数抛出异常，导致 gson 解析失败，所以替换成 kt 的反序列化
  // 但没有 alternate 属性，暂时命名两个来解决
  // 以前学弟写的，为什么不分成两个数据类呢？
  @SerialName("name")
  val name1: String? = null,
  @SerialName("stu_name")
  val name2: String? = null,

  @SerialName("stunum")
  val stunum1: String? = null,
  @SerialName("stu_num")
  val stunum2: String? = null,

  var isOpen: Boolean = false,   //是否展开，默认为false
) : Serializable, NoClassItem {

  val name: String
    get() = name1 ?: name2!!

  override val id: String
    get() = stunum1 ?: stunum2!!
}
