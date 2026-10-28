package com.example.core.tools

import com.example.core.database.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

data class ToolExecutionResult(
    val resultJson: JSONObject,
    val summary: String
)

class JarvisTools(private val db: JarvisDatabase) {

    fun getToolDeclarationsJson(): JSONArray {
        val array = JSONArray()

        // create_task
        array.put(JSONObject().apply {
            put("name", "create_task")
            put("description", "Create a task/to-do item for the user.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Short task title")
                    })
                    put("description", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional details")
                    })
                    put("priority", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "low, normal or high")
                    })
                })
                put("required", JSONArray().apply { put("title") })
            })
        })

        // list_tasks
        array.put(JSONObject().apply {
            put("name", "list_tasks")
            put("description", "List the user's currently open tasks.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // complete_task
        array.put(JSONObject().apply {
            put("name", "complete_task")
            put("description", "Mark a task as completed by matching its title.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Task title or substring to match")
                    })
                })
                put("required", JSONArray().apply { put("title") })
            })
        })

        // create_note
        array.put(JSONObject().apply {
            put("name", "create_note")
            put("description", "Save a note with a title and content body.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Note title")
                    })
                    put("content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Note body")
                    })
                })
                put("required", JSONArray().apply { put("title"); put("content") })
            })
        })

        // create_reminder
        array.put(JSONObject().apply {
            put("name", "create_reminder")
            put("description", "Schedule a reminder notification for a specific future time.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Reminder title")
                    })
                    put("remindAt", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "ISO-8601 or readable date time, e.g. 2026-10-29T10:00:00Z")
                    })
                })
                put("required", JSONArray().apply { put("title"); put("remindAt") })
            })
        })

        // save_memory
        array.put(JSONObject().apply {
            put("name", "save_memory")
            put("description", "Persist a long-term user preference or fact to the memory bank. Refuses sensitive secrets.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Fact or preference to remember")
                    })
                    put("category", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "preference, project, person, routine or general")
                    })
                })
                put("required", JSONArray().apply { put("content") })
            })
        })

        // calculate
        array.put(JSONObject().apply {
            put("name", "calculate")
            put("description", "Evaluate an arithmetic expression exactly instead of approximating.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("expression", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Arithmetic expression, e.g. (1450*0.18)+32")
                    })
                })
                put("required", JSONArray().apply { put("expression") })
            })
        })

        // current_datetime
        array.put(JSONObject().apply {
            put("name", "current_datetime")
            put("description", "Get the current date and time.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("timezone", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "IANA timezone or empty for device time")
                    })
                })
            })
        })

        return array
    }

    suspend fun execute(name: String, args: Map<String, Any?>): ToolExecutionResult {
        return try {
            when (name) {
                "create_task" -> {
                    val title = (args["title"] as? String)?.trim() ?: "Untitled task"
                    val desc = (args["description"] as? String) ?: ""
                    val priority = (args["priority"] as? String) ?: "normal"
                    val task = TaskEntity(
                        title = title,
                        description = desc,
                        priority = priority
                    )
                    db.taskDao().insert(task)
                    ToolExecutionResult(
                        resultJson = JSONObject().apply {
                            put("success", true)
                            put("taskId", task.id)
                            put("title", title)
                        },
                        summary = "Task created: $title"
                    )
                }

                "list_tasks" -> {
                    val tasks = db.taskDao().getOpenTasksList()
                    val arr = JSONArray()
                    tasks.forEach {
                        arr.put(JSONObject().apply {
                            put("title", it.title)
                            put("priority", it.priority)
                        })
                    }
                    ToolExecutionResult(
                        resultJson = JSONObject().apply { put("tasks", arr) },
                        summary = "${tasks.size} open task(s)"
                    )
                }

                "complete_task" -> {
                    val query = (args["title"] as? String)?.trim()?.lowercase(Locale.ROOT) ?: ""
                    val tasks = db.taskDao().getOpenTasksList()
                    val matched = tasks.firstOrNull { it.title.lowercase(Locale.ROOT).contains(query) }
                    if (matched != null) {
                        db.taskDao().update(matched.copy(completed = true, updatedAt = System.currentTimeMillis()))
                        ToolExecutionResult(
                            resultJson = JSONObject().apply { put("completed", matched.title) },
                            summary = "Task completed: ${matched.title}"
                        )
                    } else {
                        ToolExecutionResult(
                            resultJson = JSONObject().apply { put("error", "No matching task") },
                            summary = "No task matching '$query'"
                        )
                    }
                }

                "create_note" -> {
                    val title = (args["title"] as? String)?.trim() ?: "Note"
                    val content = (args["content"] as? String) ?: ""
                    val note = NoteEntity(title = title, content = content)
                    db.noteDao().insert(note)
                    ToolExecutionResult(
                        resultJson = JSONObject().apply {
                            put("success", true)
                            put("noteId", note.id)
                            put("title", title)
                        },
                        summary = "Note saved: $title"
                    )
                }

                "create_reminder" -> {
                    val title = (args["title"] as? String)?.trim() ?: "Reminder"
                    val remindAtStr = args["remindAt"] as? String
                    val remindAt = parseDateTime(remindAtStr) ?: (System.currentTimeMillis() + 3600_000)
                    val reminder = ReminderEntity(title = title, remindAt = remindAt)
                    db.reminderDao().insert(reminder)
                    val df = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    val formatted = df.format(Date(remindAt))
                    ToolExecutionResult(
                        resultJson = JSONObject().apply {
                            put("success", true)
                            put("reminderId", reminder.id)
                            put("remindAt", formatted)
                        },
                        summary = "Reminder set for $formatted: $title"
                    )
                }

                "save_memory" -> {
                    val content = (args["content"] as? String)?.trim() ?: ""
                    val sensitivePattern = Pattern.compile("(api[\\s_-]?key|password|secret|token|credit card)", Pattern.CASE_INSENSITIVE)
                    if (sensitivePattern.matcher(content).find()) {
                        ToolExecutionResult(
                            resultJson = JSONObject().apply { put("error", "Refused sensitive data") },
                            summary = "Memory refused: sensitive keyword detected"
                        )
                    } else {
                        val category = (args["category"] as? String) ?: "general"
                        val memory = MemoryEntity(content = content, category = category)
                        db.memoryDao().insert(memory)
                        ToolExecutionResult(
                            resultJson = JSONObject().apply { put("saved", true); put("category", category) },
                            summary = "Memory stored ($category)"
                        )
                    }
                }

                "calculate" -> {
                    val expr = (args["expression"] as? String) ?: "0"
                    val res = safeEval(expr)
                    ToolExecutionResult(
                        resultJson = JSONObject().apply { put("expression", expr); put("result", res) },
                        summary = "$expr = $res"
                    )
                }

                "current_datetime" -> {
                    val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a z", Locale.getDefault())
                    val nowStr = sdf.format(Date())
                    ToolExecutionResult(
                        resultJson = JSONObject().apply { put("currentDateTime", nowStr) },
                        summary = nowStr
                    )
                }

                else -> {
                    ToolExecutionResult(
                        resultJson = JSONObject().apply { put("error", "Unknown tool") },
                        summary = "Executed: $name"
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                resultJson = JSONObject().apply { put("error", e.localizedMessage ?: "Failed") },
                summary = "Failed to run $name"
            )
        }
    }

    private fun parseDateTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                sdf.timeZone = TimeZone.getDefault()
                val d = sdf.parse(raw)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun safeEval(expression: String): Double {
        val sanitized = expression.replace(" ", "").replace("x", "*").replace("X", "*")
        return ExpressionEvaluator(sanitized).eval()
    }

    private class ExpressionEvaluator(private val expr: String) {
        private var pos = 0

        private fun peek(): Char = if (pos < expr.length) expr[pos] else '\u0000'
        private fun get(): Char = if (pos < expr.length) expr[pos++] else '\u0000'

        fun eval(): Double = parseExpr()

        private fun parseFactor(): Double {
            if (peek() == '+') { get(); return parseFactor() }
            if (peek() == '-') { get(); return -parseFactor() }

            if (peek() == '(') {
                get() // '('
                val v = parseExpr()
                if (peek() == ')') get()
                return v
            }

            val start = pos
            while (peek() in '0'..'9' || peek() == '.') {
                get()
            }
            val numStr = expr.substring(start, pos)
            return numStr.toDoubleOrNull() ?: 0.0
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                when (peek()) {
                    '*' -> { get(); v *= parseFactor() }
                    '/' -> {
                        get()
                        val denom = parseFactor()
                        v = if (denom != 0.0) v / denom else 0.0
                    }
                    '%' -> { get(); v %= parseFactor() }
                    else -> return v
                }
            }
        }

        private fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { get(); v += parseTerm() }
                    '-' -> { get(); v -= parseTerm() }
                    else -> return v
                }
            }
        }
    }
}
