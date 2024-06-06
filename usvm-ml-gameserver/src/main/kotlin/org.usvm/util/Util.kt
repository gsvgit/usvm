package org.usvm.util

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.toType
import java.io.File


fun loadClasspathFromJar(jar: String): List<File> {
    return parseClasspath(jar)
}

fun parseClasspath(classpath: String): List<File> =
    classpath
        .split(File.pathSeparatorChar)
        .map { File(it) }

fun JcClasspath.getJcMethodByName(className: String, methodName: String): JcTypedMethod {
    val jcClass = findClass(className).toType()
    return jcClass.declaredMethods.first { it.name == methodName }
}
