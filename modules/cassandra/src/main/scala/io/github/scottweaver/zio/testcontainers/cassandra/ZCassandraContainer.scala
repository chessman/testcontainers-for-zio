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

package io.github.scottweaver.zio.testcontainers.cassandra

import zio._
import com.dimafeng.testcontainers.CassandraContainer
import java.net.InetSocketAddress
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint

object ZCassandraContainer {

  type Provides = Has[CassandraContainer] with Has[CqlSession]
  type Settings = CassandraContainer.Def

  object Settings {
    val default = ZLayer.succeed(CassandraContainer.Def())
  }

  val session = ZIO.service[CqlSession]

  val live: ZLayer[Has[Settings], Nothing, Provides] =
    ZLayer.fromManagedMany {

      def makeContainer(settings: Settings) =
        ZManaged.make(
          ZIO.effect {
            val container = settings.createContainer()
            container.start()
            container
          }.orDie
        ) { container =>
          ZIO.effect(container.stop()).orDie
        }

      def makeSession(container: CassandraContainer) =
        ZManaged.make(
          ZIO.effect {
            CqlSession.builder
              .addContactEndPoint(
                new DefaultEndPoint(
                  InetSocketAddress
                    .createUnresolved(
                      container.cassandraContainer.getHost(),
                      container.cassandraContainer.getFirstMappedPort.intValue()
                    )
                )
              )
              .withLocalDatacenter("datacenter1")
              .build()
          }.orDie
        )(session => ZIO.effect(session.close()).orDie)

      for {
        settings  <- ZIO.service[Settings].toManaged_
        container <- makeContainer(settings)
        session   <- makeSession(container)
      } yield Has(container) ++ Has(session)
    }

}
