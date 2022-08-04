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

import zio._

import java.nio.file._

package object zillen {

  type Container = models.InspectContainerResponse

  type ContainerId = models.ContainerId.Type

  type ContainerName = models.ContainerName.Type

  type DockerIO[-R, +A] = ZIO[R, DockerContainerFailure, A]

  type DockerSocketPath = DockerSettings.DockerSocketPath.Type

  type ExposedPorts = models.ProtocolPort.Exposed

  type Env = models.Env

  type HostConfig = models.HostConfig

  type HostInterface = models.HostInterface

  type Image = models.Image

  type InspectContainerPromise[E, A] = Promise[E, (Container, A)]

  type PortMap = models.PortMap

  type Protocol = models.Protocol

  type ProtocolPort = models.ProtocolPort

  private[zillen] def validFilePath(path: String): DockerIO[Any, Path] = {
    val validPath: DockerIO[Any, Path] = ZIO.attempt(Paths.get(path)).catchAll {
      case ipe: InvalidPathException =>
        Docker.failInvalidConfig(s"'${path}' does not appear to be a valid file system path.", Some(ipe))
      case t: Throwable => ZIO.die(t)
    }

    def pathExists(path: Path) = ZIO.attempt(Files.exists(path)).orDie.flatMap { exists =>
      if (!exists)
        Docker.failInvalidRuntimeState(
          s"The docker socket file, `${path}`, does not exist. Please verify that Docker is installed or you have started the Docker Desktop application."
        )
      else ZIO.unit
    }

    for {
      path <- validPath
      _    <- pathExists(path)
    } yield path
  }

}
