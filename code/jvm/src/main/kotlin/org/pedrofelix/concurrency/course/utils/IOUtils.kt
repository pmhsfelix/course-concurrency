package org.pedrofelix.concurrency.course.utils

import java.io.BufferedWriter

fun BufferedWriter.writeLine(value: String) {
    write(value)
    newLine()
    flush()
}
