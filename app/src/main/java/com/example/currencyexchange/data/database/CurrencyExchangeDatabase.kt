package com.example.currencyexchange.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.currencyexchange.data.converters.DateConverter
import com.example.currencyexchange.data.entities.*
import com.example.currencyexchange.data.dao.*

@Database(
    entities = [
        PartnerEntity::class,
        TransactionEntity::class,
        ExchangeRateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class CurrencyExchangeDatabase : RoomDatabase() {
    abstract fun partnerDao(): PartnerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun exchangeRateDao(): ExchangeRateDao

    companion object {
        @Volatile
        private var INSTANCE: CurrencyExchangeDatabase? = null

        fun getDatabase(context: Context): CurrencyExchangeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CurrencyExchangeDatabase::class.java,
                    "currency_exchange_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Insert default exchange rates
                db.execSQL("""
                    INSERT INTO exchange_rates (currency, rate, date, isDefault, source) 
                    VALUES ('CNY', 376.0, ${System.currentTimeMillis()}, 1, 'DEFAULT')
                """)
                db.execSQL("""
                    INSERT INTO exchange_rates (currency, rate, date, isDefault, source) 
                    VALUES ('USDT', 2380.0, ${System.currentTimeMillis()}, 1, 'DEFAULT')
                """)
            }
        }
    }
}