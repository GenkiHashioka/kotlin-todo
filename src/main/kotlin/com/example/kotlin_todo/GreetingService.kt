package com.example.kotlin_todo

import org.springframework.stereotype.Service

@Service
class GreetingService {
    fun greet(name: String) = "Hello, $name!"
}