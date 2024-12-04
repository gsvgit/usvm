package org.usvm.runner.util

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.impl.JcSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import java.io.File

const val mapsKey = "maps"

class JacoDBContainer(
    classpath: List<File>,
    builder: JcSettings.() -> Unit,
) {
    val db: JcDatabase
    val cp: JcClasspath

    init {
        val (db, cp) = runBlocking {
            val db = jacodb {
                builder()
                loadByteCode(classpath)
            }

            val cp = db.classpath(classpath)
            db to cp
        }
        this.db = db
        this.cp = cp
        runBlocking {
            db.awaitBackgroundJobs()
        }
    }

    companion object {
        private val keyToJacoDBContainer = HashMap<Any?, JacoDBContainer>()

        operator fun invoke(
            key: Any?,
            classpath: List<File>,
            builder: JcSettings.() -> Unit = defaultBuilder,
        ): JacoDBContainer =
            keyToJacoDBContainer.getOrPut(key) { JacoDBContainer(classpath, builder) }

        private val defaultBuilder: JcSettings.() -> Unit = {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy)
        }
    }
}