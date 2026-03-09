package blinker.go.data.extension

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ExtensionStorage(context: Context, extensionId: String) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)

    fun get(keysJson: String?): String {
        val result = JSONObject()
        fun putVal(k: String, defVal: Any? = null) {
            val stored = prefs.getString(k, null)
            if (stored != null) {
                try {
                    result.put(k, JSONObject(stored).get("v"))
                } catch(e: Exception){}
            } else if (defVal != null) {
                result.put(k, defVal)
            }
        }
        
        if (keysJson == null || keysJson == "null") {
            prefs.all.keys.forEach { k -> putVal(k) }
        } else if (keysJson.startsWith("[")) {
            try {
                val arr = JSONArray(keysJson)
                for (i in 0 until arr.length()) { putVal(arr.getString(i)) }
            } catch(e: Exception){}
        } else if (keysJson.startsWith("{")) {
            try {
                val defaultObj = JSONObject(keysJson)
                defaultObj.keys().forEach { k -> putVal(k, defaultObj.get(k)) }
            } catch(e: Exception){}
        } else {
            val k = keysJson.replace("\"", "")
            putVal(k)
        }
        return result.toString()
    }

    fun set(itemsJson: String) {
        try {
            val obj = JSONObject(itemsJson)
            val edit = prefs.edit()
            obj.keys().forEach { k ->
                val wrapper = JSONObject()
                wrapper.put("v", obj.get(k))
                edit.putString(k, wrapper.toString())
            }
            edit.apply()
        } catch(e: Exception){}
    }

    fun remove(keysJson: String) {
        val edit = prefs.edit()
        if (keysJson.startsWith("[")) {
            try {
                val arr = JSONArray(keysJson)
                for(i in 0 until arr.length()){
                    edit.remove(arr.getString(i))
                }
            } catch(e: Exception){}
        } else {
            edit.remove(keysJson.replace("\"", ""))
        }
        edit.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
