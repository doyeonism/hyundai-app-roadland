package com.example.myapplication

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exports this isolated recording's timestamps through the normal test runner. */
@RunWith(AndroidJUnit4::class)
class ShowcaseExportTest {
 @Test fun exportChapters() {
  val ins=InstrumentationRegistry.getInstrumentation()
  check(ins.targetContext.packageName.endsWith(".showcase"))
  val file=File(ins.targetContext.getExternalFilesDir(null),"chapters.json")
  val json=JSONArray(file.readText()).toString()
  ins.sendStatus(0,Bundle().apply { putString("CHAPTERS_JSON",json) })
 }
}
