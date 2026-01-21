package com.example.linkedinagent

import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


fun main() {

    val TAG = "MyPrograms"
    /*
     Global Scope
     - coroutine will live as long as our app does
     - coroutine dies when app dies
     - not a good idea to use
     - Coroutines from Global Scope runs in new thread

     Sleep vs delay
     - delay only pauses the coroutine
     - sleep pauses the thread

      delay
      - is a suspend function
      - delay functions can only be called in another suspend function or from coroutine.
      - we can't just use delay like we call from sleep
      - we can write our own suspend func
     Note
     - if main thread finishes its work other coroutines will be cancelled
     -
     */

    GlobalScope.launch {
        doNetworkcall()
        doNetworkcall2()
        delay(3000L)
        println("Coroutine says hello from ${Thread.currentThread().name}")

    }

    println("Hey Nandhu")
}
suspend fun doNetworkcall():String{
    delay(2000L)
    return "This is network call"
}

suspend fun doNetworkcall2():String{
    delay(2000L)
    return "This is network call"
}
