package com.example

import dispatch._
import org.specs.Specification

object ExampleSpec extends Specification with unfiltered.spec.jetty.Served {
  
  def setup = { _.filter(new App) }
  
  val http = new Http
  
  "The example app" should {
    "serve unfiltered requests" in {
      val status = http x (host as_str) {
        case (code, _, _, _) => code
      }
      status must_== 200
    }
  }
}