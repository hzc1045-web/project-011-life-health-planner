package com.project011.lifehealthplanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class OnboardingAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingHasVisiblePrimaryActionAndSafetyBoundary() {
        composeRule.onNodeWithText("建立你的个人画像").assertIsDisplayed()
        composeRule.onNodeWithText("保存并进入应用").assertIsDisplayed()
        composeRule.onNodeWithText("本软件不提供疾病诊断、处方或药物剂量调整；紧急情况请联系当地急救服务。")
            .assertIsDisplayed()
    }
}
