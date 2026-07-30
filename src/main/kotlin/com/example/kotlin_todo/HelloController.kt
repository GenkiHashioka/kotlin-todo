package com.example.kotlin_todo

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
//  コンストラクタインジェクション
class HelloController(private val greetingService: GreetingService) {
    // /helloを叩くと次の処理が行われる。
    @GetMapping("/hello")
    // nameに何も指定しなければデフォルトパラメータはWorld
    fun hello(@RequestParam name: String = "World") = mapOf("message" to greetingService.greet(name))

}
