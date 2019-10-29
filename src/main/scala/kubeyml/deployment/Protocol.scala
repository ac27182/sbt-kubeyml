package kubeyml.deployment

import scala.concurrent.duration.FiniteDuration

sealed trait KubernetesState

case class Deployment(
  apiVersion: String = "apps/v1",
  metadata: DeploymentMetadata,
  spec: Spec
) extends KubernetesState {
  private[kubeyml] def addContainerPorts(ports: List[Port]): Deployment =
    this.copy(spec = spec.addContainerPorts(ports))
  private[kubeyml] def annotateSpecTemplate(annotations: Map[String, String]): Deployment =
    this.copy(spec = spec.annotate(annotations))
  private[kubeyml] def addEnv(envs: Map[EnvName, EnvValue]): Deployment =
    this.copy(spec = spec.addContainerEnvs(envs))

  private[kubeyml] def request(resource: Resource): Deployment =
    this.copy(spec = spec.limitResource(resource))

  private[kubeyml] def limit(resource: Resource): Deployment =
    this.copy(spec = spec.limitResource(resource))
}

case class DeploymentMetadata(
  name: String,
  namespace: String
)


case class Labels(app: String)

case class Spec(
  replicas: Int,
  selector: Selector,
  template: Template,
  strategy: DeploymentStrategy
) {
  private[deployment] def addContainerPorts(ports: List[Port]): Spec =
    this.copy(template = template.addContainerPorts(ports))
  private[deployment] def annotate(annotations: Map[String, String]): Spec =
    this.copy(template = template.annotate(annotations))
  private[deployment] def addContainerEnvs(envs: Map[EnvName, EnvValue]): Spec =
    this.copy(template = template.addContainerEnvs(envs))

  private[deployment] def requestResource(resource: Resource): Spec =
    this.copy(template = template.requestResource(resource))

  private[deployment] def limitResource(resource: Resource): Spec =
    this.copy(template = template.limitResource(resource))
}

case class Selector(
  matchLabels: MatchLabels
)

case class MatchLabels(app: Option[String])

case class Template(metadata: TemplateMetadata, spec: TemplateSpec) {
  private[deployment] def addContainerPorts(ports: List[Port]): Template =
    this.copy(spec = spec.addContainerPorts(ports))

  private[deployment] def annotate(annotations: Map[String, String]): Template =
    this.copy(metadata = metadata.copy(annotations = annotations))

  private[deployment] def addContainerEnvs(envs: Map[EnvName, EnvValue]): Template =
    this.copy(spec = spec.addContainerEnvs(envs))

  private[deployment] def requestResource(resource: Resource): Template =
    this.copy(spec = spec.requestResource(resource))

  private[deployment] def limitResource(resource: Resource): Template =
    this.copy(spec = spec.limitResource(resource))
}

case class TemplateMetadata(labels: Labels, annotations: Map[String, String])

case class TemplateSpec(containers: List[Container]) {
  private[deployment] def addContainerPorts(ports: List[Port]): TemplateSpec =
    this.copy(containers = containers.map {
      container => container.copy(ports = container.ports ++ ports)
    }
  )
  private[deployment] def addContainerEnvs(envs: Map[EnvName, EnvValue]): TemplateSpec =
    this.copy(containers = containers.map(_.addEnvs(envs)))
  private[deployment] def requestResource(resource: Resource): TemplateSpec =
    this.copy(containers = containers.map(_.requestResource(resource)))

  private[deployment] def limitResource(resource: Resource): TemplateSpec =
    this.copy(containers = containers.map(_.limitResource(resource)))
}

case class Container(
    name: String,
    image: String,
    ports: List[Port],
    imagePullPolicy: ImagePullPolicy,
    livenessProbe: Probe,
    readinessProbe: Probe,
    env: Map[EnvName, EnvValue],
    resources: Resources = Resources()
) {
  private[deployment] def addEnvs(envs: Map[EnvName, EnvValue]): Container =
    this.copy(env = env ++ envs)

  private[deployment] def requestResource(resource: Resource): Container =
    this.copy(resources = resources.copy(requests = resource))
  private[deployment] def limitResource(resource: Resource): Container =
    this.copy(resources = resources.copy(limits = resource))
}

case class Resources(
      requests: Resource = Resource(Cpu(500), Memory(256)),
      limits: Resource = Resource(Cpu(1000), Memory(512))
) {
  require(requests.cpu.value <= limits.cpu.value)
  require(requests.memory.value <= limits.memory.value)
}

case class Resource(cpu: Cpu, memory: Memory)

case class Cpu(value: Int) {
  require(value > 0)
}
object Cpu {
  def fromCores(number: Short): Cpu = {
    require(number > 0 && number <= 128)
    Cpu(number*500)
  }
}
case class Memory(value: Int) {
  require(value > 0)
}

case class EnvName(value: String)
sealed trait EnvValue
case class EnvRawValue(value: String) extends EnvValue
case class EnvFieldValue(fieldPath: String) extends EnvValue
case class EnvSecretValue(name: String, key: String) extends EnvValue

case class EnvVarDefinition(name: String, value: EnvValue)

// For more probes go https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
sealed trait Probe
case class HttpProbe(httpGet: HttpGet, initialDelay: FiniteDuration, period: FiniteDuration, failureThreshold: Option[Short]) extends Probe
case object NoProbe extends Probe

case class HttpGet(path: String, port: Int, httpHeaders: List[Header])

case class Header(name: String, value: String)

case class Port(name: Option[String], containerPort: Int)

sealed trait DeploymentStrategy

case class RollingUpdate(maxSurge: Int = 0, maxUnavailable: Int = 1) extends DeploymentStrategy


sealed trait ImagePullPolicy

case object Always extends ImagePullPolicy
case object IfNotPresent extends ImagePullPolicy
