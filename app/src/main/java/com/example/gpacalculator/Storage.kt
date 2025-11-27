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

        universities.filter { it.isCustom }.forEach { uni ->
            val uniJson = JSONObject()
            uniJson.put("id", uni.id)
            uniJson.put("name", uni.name)
            uniJson.put("isCustom", true)

            // 1. Serialize Grades
            val gradesArray = JSONArray()
            uni.grades.forEach { grade ->
                val gJson = JSONObject()
                gJson.put("symbol", grade.symbol)
                gJson.put("point", grade.point)
                gJson.put("isCredit", grade.isCreditCourse)
                gradesArray.put(gJson)
            }
            uniJson.put("grades", gradesArray)

            // 2. Serialize Classifications
            val classArray = JSONArray()
            uni.classifications.forEach { rule ->
                val rJson = JSONObject()
                rJson.put("min", rule.minGpa)
                rJson.put("max", rule.maxGpa)
                rJson.put("label", rule.label)
                classArray.put(rJson)
            }
            uniJson.put("classifications", classArray)

            // 3. FIX: Serialize Percentage Rules (This was missing)
            val percentArray = JSONArray()
            uni.percentageRules.forEach { pRule ->
                val pJson = JSONObject()
                pJson.put("min", pRule.minGpa)
                pJson.put("max", pRule.maxGpa)
                pJson.put("formula", pRule.formula)
                percentArray.put(pJson)
            }
            uniJson.put("percentageRules", percentArray)

            jsonArray.put(uniJson)
        }

        prefs.edit().putString(KEY_CUSTOM_UNIS, jsonArray.toString()).apply()
    }

    fun loadCustomUniversities(context: Context): List<University> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CUSTOM_UNIS, null) ?: return emptyList()
        return parseJsonToUniversities(jsonString)
    }

    fun getExportString(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_UNIS, "[]") ?: "[]"
    }

    fun importFromString(context: Context, jsonString: String): Boolean {
        try {
            val newUnis = parseJsonToUniversities(jsonString)
            if (newUnis.isEmpty() && jsonString != "[]") return false

            val current = loadCustomUniversities(context).toMutableList()
            newUnis.forEach { newUni ->
                current.removeAll { it.id == newUni.id }
                current.add(newUni)
            }
            saveCustomUniversities(context, current)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun parseJsonToUniversities(jsonString: String): List<University> {
        val list = mutableListOf<University>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                
                // Grades
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

                // Classifications
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

                // FIX: Percentage Rules
                val percentList = mutableListOf<PercentageRule>()
                // Use optJSONArray because old saves might not have this field
                val percentArray = obj.optJSONArray("percentageRules") 
                if (percentArray != null) {
                    for (p in 0 until percentArray.length()) {
                        val pObj = percentArray.getJSONObject(p)
                        percentList.add(
                            PercentageRule(
                                minGpa = pObj.getDouble("min"),
                                maxGpa = pObj.getDouble("max"),
                                formula = pObj.getString("formula")
                            )
                        )
                    }
                }

                list.add(
                    University(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        grades = gradesList,
                        classifications = classList,
                        percentageRules = percentList,
                        isCustom = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}