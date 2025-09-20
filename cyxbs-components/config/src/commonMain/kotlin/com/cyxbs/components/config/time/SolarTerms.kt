package com.cyxbs.components.config.time

import kotlinx.datetime.LocalDate

/**
 * http://www.cnterm.cn/kxcb/kpwz/201603/t20160307_328007.html
 *
 * @author 985892345
 * 2024/2/17 16:57
 */
enum class SolarTerms(
  val chinese: String,
  private val baseDate: Int
) {

  BeginningOfSpring("立春", 3),
  RainWater("雨水", 18),
  AwakeningFromHibernation("惊蛰", 4),
  SpringEquinox("春分", 19),
  FreshGreen("清明", 4),
  GrainRain("谷雨", 19),
  BeginningOfSummer("立夏", 4),
  LesserFullness("小满", 20),
  GrainInEar("芒种", 4),
  SummerSolstice("夏至", 20),
  LesserHeat("小暑", 6),
  GreaterHeat("大暑", 22),
  BeginningOfAutumn("立秋", 6),
  EndOfHeat("处暑", 22),
  WhiteDew("白露", 6),
  AutumnalEquinox("秋分", 22),
  ColdDew("寒露", 7),
  FirstFrost("霜降", 22),
  BeginningOfWinter("立冬", 6),
  LightSnow("小雪", 21),
  HeavySnow("大雪", 6),
  WinterSolstice("冬至", 21),
  LesserCold("小寒", 4),
  GreaterCold("大寒", 19);

  fun getLocalDate(year: Int): LocalDate {
    return LocalDate(year, getMonth(), getDayOfMonth(year))
  }

  fun getMonth(): Int {
    return (ordinal / 2 + 1) % 12 + 1
  }

  fun getDayOfMonth(year: Int): Int {
    val info = infos[year - 1900]
    return ((info and (0x3L shl (24 - ordinal * 2))) ushr (24 - ordinal * 2)).toInt() + baseDate
  }

  companion object {

    fun get(date: Date): SolarTerms? {
      val dayDiff = if (date.dayOfMonth < 15) 0F else 0.5F
      val ordinal = ((date.monthNumber + dayDiff) * 2 + 20).toInt() % 24
      val solarTerms = entries[ordinal]
      return if (solarTerms.getDayOfMonth(date.year) == date.dayOfMonth) solarTerms else null
    }

    private val infos = longArrayOf(
      0x5a59a599aa59, 0x5a6aa9aaaa9a, 0x9aaaeaaaaaaa, 0xafaeeaeeaaaa, 0xaa59a599aa5e, // 1900-1904
      0x5a6aa9aaaa9a, 0x9aaaaaaaaaaa, 0xafaeeaeeaaaa, 0xaa59a599aa5e, 0x5a6aa9aaaa9a, // 1905-1909
      0x9aaaaaaaaaaa, 0xafaeeaeeaaaa, 0xaa59a599aa5e, 0x5a6aa9aaaa99, 0x5a6aaaaaaaaa, // 1910-1914
      0xabaaeaaeaaaa, 0xaa59a599695a, 0x5a69a9a9aa59, 0x5a6aaaaaaa9a, 0xabaaeaaeaaaa, // 1915-1919
      0xaa59a599695a, 0x5a59a9a9aa59, 0x5a6aaaaaaa9a, 0x9aaaeaaeaaaa, 0xaa59a599695a, // 1920-1924
      0x5a59a9a9aa59, 0x5a6aa9aaaa9a, 0x9aaaeaaaaaaa, 0xaa599599555a, 0x5a59a599aa59, // 1925-1929
      0x5a6aa9aaaa9a, 0x9aaaeaaaaaaa, 0xaa599599555a, 0x5a59a599aa59, 0x5a6aa9aaaa9a, // 1930-1934
      0x9aaaaaaaaaaa, 0xaa599599555a, 0x5a59a599aa59, 0x5a6aa9aaaa9a, 0x9aaaaaaaaaaa, // 1935-1939
      0xaa599599555a, 0x5a59a599aa59, 0x5a6aa9aaaa9a, 0x9aaaaaaaaaaa, 0xaa559599555a, // 1940-1944
      0x5a59a5996959, 0x5a6aa9a9aa99, 0x5a6aaaaaaaaa, 0xa6559559555a, 0x5a59a5996955, // 1945-1949
      0x5a59a9a9aa99, 0x5a6aaaaaaaaa, 0xa6559559555a, 0x5a59a5996955, 0x5a59a9a9aa59, // 1950-1954
      0x5a6aa9aaaa9a, 0xa5559559555a, 0x5a59a5996955, 0x5a59a599aa59, 0x5a6aa9aaaa9a, // 1955-1959
      0x95559555555a, 0x5a5995995555, 0x5a59a599aa59, 0x5a6aa9aaaa9a, 0x95559555555a, // 1960-1964
      0x5a5995995555, 0x5a59a599aa59, 0x5a6aa9aaaa9a, 0x95555555555a, 0x5a5995995555, // 1965-1969
      0x5a59a599aa59, 0x5a6aa9aaaa9a, 0x95555555555a, 0x5a5595995555, 0x5a59a599aa59, // 1970-1974
      0x5a6aa9a9aa9a, 0x95155555555a, 0x5a5595595555, 0x5a59a5996a59, 0x5a69a9a9aa9a, // 1975-1979
      0x95155555555a, 0x5a5595595555, 0x5a59a5996959, 0x5a59a9a9aa99, 0x55155455555a, // 1980-1984
      0x565595595555, 0x5a59a5996955, 0x5a59a5a9aa59, 0x55155455554a, 0x555595555555, // 1985-1989
      0x5a5995996955, 0x5a59a599aa59, 0x55155455554a, 0x455595555555, 0x5a5995995555, // 1990-1994
      0x5a59a599aa59, 0x55155455554a, 0x455555555555, 0x5a5995995555, 0x5a59a599aa59, // 1995-1999
      0x55155455554a, 0x455555555555, 0x5a5995995555, 0x5a59a599aa59, 0x55155455554a, // 2000-2004
      0x455555555555, 0x5a5595595555, 0x5a59a599aa59, 0x55155454554a, 0x451555555555, // 2005-2009
      0x5a5595595555, 0x5a59a5996a59, 0x55145454554a, 0x451554555555, 0x5a5595595555, // 2010-2014
      0x5a59a5996959, 0x550454545549, 0x051554555555, 0x565595595555, 0x5a5995996955, // 2015-2019
      0x550450445549, 0x051554555545, 0x555595555555, 0x5a5995996955, 0x550450445509, // 2020-2024
      0x051554555545, 0x455555555555, 0x5a5995995555, 0x550450445509, 0x051554555545, // 2025-2029
      0x455555555555, 0x5a5995995555, 0x550450445509, 0x051554555545, 0x455555555555, // 2030-2034
      0x5a5595595555, 0x550450445509, 0x051554555545, 0x455555555555, 0x5a5595595555, // 2035-2039
      0x550450445509, 0x051454545545, 0x451555555555, 0x5a5595595555, 0x550450441509, // 2040-2044
      0x050454545545, 0x451554555555, 0x5a5595595555, 0x550440441409, 0x050450445544, // 2045-2049
      0x051554555555, 0x555595555555, 0x550440441405, 0x050450445544, 0x051554555555, // 2050-2054
      0x555555555555, 0x550440441405, 0x050450445504, 0x051554555545, 0x555555555555, // 2055-2059
      0x550440440005, 0x050450445504, 0x051554555545, 0x455555555555, 0x550440440005, // 2060-2064
      0x050450445504, 0x051554555545, 0x455555555555, 0x550040040005, 0x050450445504, // 2065-2069
      0x051454545545, 0x455555555555, 0x550040040005, 0x050450441504, 0x051454545545, // 2070-2074
      0x451554555555, 0x550040040005, 0x050450441504, 0x050450545545, 0x451554555555, // 2075-2079
      0x550040040005, 0x050440441404, 0x050450445545, 0x051554555555, 0x500040000005, // 2080-2084
      0x050440441400, 0x050450445544, 0x051554555555, 0x500000000005, 0x050440441400, // 2085-2089
      0x050450445504, 0x051554555545, 0x500000000005, 0x050440440000, 0x050450445504, // 2090-2094
      0x051554555545, 0x400000000005, 0x050440040000, 0x050450445504, 0x051554555545, // 2095-2099
      0x455555555555,
    )
  }
}