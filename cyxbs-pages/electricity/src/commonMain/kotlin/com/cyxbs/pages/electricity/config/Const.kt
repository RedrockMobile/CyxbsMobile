package com.cyxbs.pages.electricity.config

/**
 * 电费查询本地持久化 key
 */

const val SP_BUILDING_HEAD_KEY = "select_building_head_position"
const val SP_BUILDING_FOOT_KEY = "select_building_foot_position"
const val SP_ROOM_KEY = "select_room_position"

val BUILDING_NAMES_HEADER = listOf("知行苑", "兴业苑", "四海苑", "宁静苑", "明理苑", "明志苑")
val BUILDING_NAMES: Map<String, List<String>> = mapOf(
  "知行苑" to listOf(
    "1舍(01栋)",
    "2舍(02栋)",
    "3舍(03栋)",
    "4舍(04栋)",
    "5舍(05栋)",
    "6舍(06栋)",
    "7舍(15栋)",
    "8舍(16栋)"
  ),
  "兴业苑" to listOf(
    "1舍(17栋)",
    "2舍(18栋)",
    "3舍(19栋)",
    "4舍(20栋)",
    "5舍(21栋)",
    "6舍(22栋)",
    "7舍(23A栋)",
    "8舍(23B栋)"
  ),
  "四海苑" to listOf(
    "1舍(36栋)",
    "2舍(37栋)"
  ),
  "宁静苑" to listOf(
    "1舍(08栋)",
    "2舍(09栋)",
    "3舍(10栋)",
    "4舍(11栋)",
    "5舍(12栋)",
    "6舍(32栋)",
    "7舍(33栋)",
    "8舍(34栋)",
    "9舍(35栋)",
    "10舍(40栋)"
  ),
  "明理苑" to listOf(
    "1舍(24栋)",
    "2舍(25栋)",
    "3舍(26栋)",
    "4舍(27栋)",
    "5舍(28栋)",
    "6舍(29栋)",
    "7舍(30栋)",
    "8舍(31栋)",
    "9舍(39栋)"
  ),
  "明志苑" to listOf(
    "1舍(45栋)",
    "2舍(46栋)",
    "3舍(47栋)"
  ),
)

/**
 * 从形如 "1舍(01栋)" 的展示文本中解出后端需要的楼栋 id（即 01）
 */
internal fun parseBuildingId(label: String): String =
  label.substringAfter('(').substringBefore('栋')
