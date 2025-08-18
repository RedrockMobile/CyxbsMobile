package com.cyxbs.pages.qa.home.interfaces

/**
 * description ： 刷新ui
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/18 13:58
 */

/*
为了解决在第一个fragment点赞第二个frgament不能更新数据的问题每次滑动到下一个fragment时会重绘一次ui，使用差分刷新就不会出现闪一下的情况
根本原因还是因为fragment的缓存机制导致的,因为缓存机制的存在，用户在第一个fragment里面实现了点赞操作，第二个fragment早就画好了，所以这里重绘利用差分刷新 不消耗性能的情况下完成更新
 */
interface Refreshable {
    fun refreshUI()
}