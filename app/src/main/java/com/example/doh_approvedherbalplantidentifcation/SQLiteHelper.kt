package com.example.doh_approvedherbalplantidentifcation

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class SQLiteHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "HerbalPlants.db"

        // Table and column names
        const val TABLE_NAME = "herbs"
        const val COL_ID = "id"
        const val COL_NAME = "herbalname"
        const val COL_LEVEL = "herballevel"
        const val COL_DESCRIPTION = "herbaldescription"
        const val COL_SAFETY = "herbalsafetywarn"
        const val COL_IMAGE = "herbalimage"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_LEVEL REAL NOT NULL,
                $COL_DESCRIPTION TEXT,
                $COL_SAFETY TEXT,
                $COL_IMAGE BLOB
            )
        """.trimIndent()
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertHerb(
        herbalname: String,
        herballevel: Float,
        herbaldescription: String,
        herbalsafetywarn: String,
        herbalimage: ByteArray
    ): Boolean {
        var success = false
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put(COL_NAME, herbalname)
                put(COL_LEVEL, herballevel)
                put(COL_DESCRIPTION, herbaldescription)
                put(COL_SAFETY, herbalsafetywarn)
                put(COL_IMAGE, herbalimage)
            }

            val result = db.insert(TABLE_NAME, null, values)
            success = result != -1L

            if (!success) {
                Log.e("SQLiteHelper", "Insert returned -1L (failure)")
            }
        } catch (e: Exception) {
            Log.e("SQLiteHelper", "Insert failed: ${e.message}", e)
        } finally {
            db.close()
        }

        return success
    }

    fun getAllHerbs(): List<HerbModel> {
        val herbList = mutableListOf<HerbModel>()
        val db = readableDatabase

        try {
            // CHANGED: We explicitly select only text columns. We DO NOT select COL_IMAGE.
            val cursor = db.rawQuery(
                "SELECT $COL_ID, $COL_NAME, $COL_LEVEL, $COL_DESCRIPTION, $COL_SAFETY FROM $TABLE_NAME",
                null
            )

            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
                    val level = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_LEVEL))
                    val description = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION))
                    val safety = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAFETY))

                    // CHANGED: Since we didn't select the image, we create an empty placeholder.
                    // This prevents the "Row too big" crash.
                    val imagePlaceholder = ByteArray(0)

                    herbList.add(HerbModel(id, name, level, description, safety, imagePlaceholder))
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("SQLiteHelper", "Error reading herbs: ${e.message}", e)
        } finally {
            db.close()
        }

        return herbList
    }


    // 👉 New paginated query method
    fun getHerbs(limit: Int, offset: Int): List<HerbModel> {
        val herbList = mutableListOf<HerbModel>()
        val db = readableDatabase

        try {
            val cursor = db.rawQuery(
                "SELECT * FROM $TABLE_NAME LIMIT ? OFFSET ?",
                arrayOf(limit.toString(), offset.toString())
            )
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
                    val level = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_LEVEL))
                    val description = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION))
                    val safety = cursor.getString(cursor.getColumnIndexOrThrow(COL_SAFETY))
                    val image = cursor.getBlob(cursor.getColumnIndexOrThrow(COL_IMAGE))

                    herbList.add(HerbModel(id, name, level, description, safety, image))
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("SQLiteHelper", "Error reading herbs: ${e.message}", e)
        } finally {
            db.close()
        }

        return herbList
    }

    // Inside your SQLiteHelper
    fun deleteHerb(id: Int): Boolean {
        val db = this.writableDatabase
        val result = db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }
    fun clearAllHerbs(): Boolean {
        val db = this.writableDatabase
        return try {
            // Passing null to whereClause deletes all rows
            val result = db.delete(TABLE_NAME, null, null)
            db.close()
            result >= 0
        } catch (e: Exception) {
            Log.e("SQLiteHelper", "Error clearing table: ${e.message}")
            false
        }
    }


    fun getSavedPlantsCount(): Int {
        val db = this.readableDatabase
        // DISTINCT ensures "Lagundi" is only counted once, no matter how many entries exist
        val query = "SELECT COUNT(DISTINCT $COL_NAME) FROM $TABLE_NAME"
        val cursor = db.rawQuery(query, null)

        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }

        cursor.close()
        db.close()
        return count
    }
}