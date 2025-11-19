package com.example.gpacalculator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Storage {
    private const val PREF_NAME = "gpa_prefs"
    private const val KEY_CUSTOM_UNIS = "custom_universities"

    fun saveCustomUniversities(context: Context, universities: List<University>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()

        universities.forEach { uni ->
            val uniJson = JSONObject()
            uniJson.put("id", uni.id)
            uniJson.put("name", uni.name)

            // Serialize Grades
            val gradesArray = JSONArray()
            uni.grades.forEach { grade ->
                val gJson = JSONObject()
                gJson.put("symbol", grade.symbol)
                gJson.put("point", grade.point)
                gJson.put("isCredit", grade.isCreditCourse)
                gradesArray.put(gJson)
            }
            uniJson.put("grades", gradesArray)

            // Serialize Classifications
            val classArray = JSONArray()
            uni.classifications.forEach { rule ->
                val rJson = JSONObject()
                rJson.put("min", rule.minGpa)
                rJson.put("max", rule.maxGpa)
                rJson.put("label", rule.label)
                classArray.put(rJson)
            }
            uniJson.put("classifications", classArray)

            jsonArray.put(uniJson)
        }

        prefs.edit().putString(KEY_CUSTOM_UNIS, jsonArray.toString()).apply()
    }

    fun loadCustomUniversities(context: Context): List<University> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CUSTOM_UNIS, null) ?: return emptyList()
        val list = mutableListOf<University>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                
                // Parse Grades
                val gradesList = mutableListOf<Grade>()
                val gradesArray = obj.getJSONArray("grades")
                for (j in 0 until gradesArray.length()) {
                    val gObj = gradesArray.getJSONObject(j)
                    gradesList.add(
                        Grade(
                            symbol = gObj.getString("symbol"),
                            point = gObj.getDouble("point"),
                            isCreditCourse = gObj.optBoolean("isCredit", true)
                        )
                    )
                }

                // Parse Classifications
                val classList = mutableListOf<ClassificationRule>()
                val classArray = obj.getJSONArray("classifications")
                for (k in 0 until classArray.length()) {
                    val cObj = classArray.getJSONObject(k)
                    classList.add(
                        ClassificationRule(
                            minGpa = cObj.getDouble("min"),
                            maxGpa = cObj.getDouble("max"),
                            label = cObj.getString("label")
                        )
                    )
                }

                list.add(
                    University(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        grades = gradesList,
                        classifications = classList
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}