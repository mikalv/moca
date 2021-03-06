package com.github.lucastorri.moca.criteria

import com.github.lucastorri.moca.browser.RenderedPage
import com.github.lucastorri.moca.role.Task
import com.github.lucastorri.moca.role.worker.Link
import com.github.lucastorri.moca.url.Url
import netscape.javascript.JSObject

import scala.util.Try

trait JavaScriptCriteria extends LinkSelectionCriteria {

  def script: String

  override def select(task: Task, link: Link, page: RenderedPage): Set[Url] = {
    val obj = page.exec(script).asInstanceOf[JSObject]
    val length = Try(obj.getMember("length").asInstanceOf[Number].intValue).getOrElse(0)
    val url = page.renderedUrl
    (0 until length).flatMap(i => url.resolveOption(obj.getSlot(i).toString)).toSet
  }

}

case class StringJSCriteria(script: String) extends JavaScriptCriteria