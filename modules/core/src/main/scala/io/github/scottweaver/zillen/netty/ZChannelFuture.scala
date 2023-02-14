/*
 * Copyright 2021 io.github.scottweaver
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.scottweaver
package zillen
package netty

import io.netty.util.concurrent.{Future, GenericFutureListener}
import zio._

import java.util.concurrent.CancellationException

/**
 * Lovingly lifted from zio.http :D
 *
 * @param jFuture
 */
private[zillen] final class ZChannelFuture[A] private (jFuture: Future[A]) {

  /**
   * Resolves when the underlying future resolves and removes the handler
   * (output: A) - if the future is resolved successfully (cause: None) - if the
   * future fails with a CancellationException (cause: Throwable) - if the
   * future fails with any other Exception
   */
  def execute: Task[Option[A]] = {
    var handler: GenericFutureListener[Future[A]] = { _ => }
    ZIO
      .async[Any, Throwable, Option[A]] { cb =>
        handler = _ =>
          jFuture.cause() match {
            case null                     => cb(ZIO.attempt(Option(jFuture.get)))
            case _: CancellationException => cb(ZIO.succeed(Option.empty))
            case cause                    => cb(ZIO.fail(cause))
          }
        val _ = jFuture.addListener(handler)
      }
      .onInterrupt(ZIO.succeed(jFuture.removeListener(handler)))
  }

  def scoped: ZIO[Scope, Throwable, Option[A]] =
    execute.withFinalizer(_ => cancel(true))

  // Cancels the future
  def cancel(interruptIfRunning: Boolean = false): UIO[Boolean] = ZIO.succeed(jFuture.cancel(interruptIfRunning))
}

object ZChannelFuture {
  def make[A](jFuture: => Future[A]): Task[ZChannelFuture[A]] = ZIO.attempt(new ZChannelFuture(jFuture))

  def unit[A](jFuture: => Future[A]): Task[Unit] = make(jFuture).flatMap(_.execute.unit)

  def scoped[A](jFuture: => Future[A]): ZIO[Scope, Throwable, Unit] = make(jFuture).flatMap(_.scoped.unit)
}
