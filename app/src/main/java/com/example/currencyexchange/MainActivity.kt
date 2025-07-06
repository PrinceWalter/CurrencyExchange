package com.example.currencyexchange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import com.example.currencyexchange.ui.theme.CurrencyExchangeTheme
import com.example.currencyexchange.ui.screens.SimpleMainPage
import com.example.currencyexchange.ui.screens.SimplePartnerPage

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CurrencyExchangeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main_page"
                    ) {
                        composable("main_page") {
                            SimpleMainPage(navController = navController)
                        }

                        composable(
                            route = "partner_page/{partnerId}",
                            arguments = listOf(
                                navArgument("partnerId") {
                                    type = NavType.LongType
                                }
                            )
                        ) { backStackEntry ->
                            val partnerId = backStackEntry.arguments?.getLong("partnerId") ?: 0L
                            SimplePartnerPage(
                                partnerId = partnerId,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}
