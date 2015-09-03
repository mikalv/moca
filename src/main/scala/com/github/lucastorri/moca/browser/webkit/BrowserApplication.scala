package com.github.lucastorri.moca.browser.webkit

import javafx.application.{Application, Platform}
import javafx.scene.Scene
import javafx.stage.Stage

import com.github.lucastorri.moca.async.runnable

class BrowserApplication extends Application {

  override def start(stage: Stage): Unit =
    BrowserWindow.register(this)

  def newWindow(settings: WebKitSettings): Unit = {
    Platform.runLater(runnable {
      val browser = new BrowserWindow(settings)
      val scene = new Scene(browser, settings.width, settings.height)
      val stage = new Stage()
      stage.setScene(scene)
      stage.show()
      BrowserWindow.release(browser)
    })
  }

}
