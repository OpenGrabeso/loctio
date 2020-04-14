package com.github.opengrabeso.loctio
package requests

import java.util.concurrent.{ConcurrentLinkedQueue, Semaphore, ThreadFactory}

import com.google.appengine.api.ThreadManager
import com.google.appengine.api.taskqueue.{DeferredTask, QueueFactory, TaskHandle, TaskOptions}
import com.google.appengine.api.utils.SystemProperty

object BackgroundTasks {
  trait Tasks {
    def addTask(t: DeferredTask): Unit
  }

  object LocalTaskQueue extends Runnable with Tasks with ThreadFactory {
    val q = new ConcurrentLinkedQueue[Option[DeferredTask]]
    val issued = new Semaphore(0)

    // initialization will be called on a first access (when first task is added)
    val thread = new Thread(this)
    thread.setDaemon(true)
    thread.start()

    def addTask(t: DeferredTask) = {
      q.add(Some(t))
      issued.release()
    }

    def terminate() = addTask(null)

    @scala.annotation.tailrec
    def run() = {
      issued.acquire(1)
      val t = q.poll()
      t match {
        case Some(task) =>
          task.run()
          run()
        case None =>
      }
    }
    def newThread(r: Runnable) = {
      val thread = new Thread(r)
      thread
    }
  }

  object ApplTaskQueue extends Tasks {
    def addTask(task: DeferredTask): Unit = {
      val queue = QueueFactory.getDefaultQueue
      queue add TaskOptions.Builder.withPayload(task)
    }
  }

  private val appEngine = SystemProperty.environment.value() != null

  def addTask(task: DeferredTask): Unit = {
    if (appEngine) {
      ApplTaskQueue.addTask(task)
    } else {
      LocalTaskQueue.addTask(task)
    }
  }

  def currentRequestThreadFactory: ThreadFactory = {
    if (appEngine) {
      ThreadManager.currentRequestThreadFactory
    } else {
      LocalTaskQueue
    }
  }
}
