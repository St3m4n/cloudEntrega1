package cl.duoc.pedidos360.kafkaadmin;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
@RestController @RequestMapping("/api/admin/kafka")
public class KafkaAdminController {
    private final AdminClient admin;
    public KafkaAdminController(AdminClient admin) { this.admin=admin; }
    @Configuration static class Config {
        @Bean(destroyMethod="close") AdminClient adminClient(@Value("${spring.kafka.bootstrap-servers}") String servers) { return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,servers,AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,5000,AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,10000)); }
    }
    public record TopicInput(@NotBlank @Pattern(regexp="[a-zA-Z0-9._-]+") String name,@Min(1) int partitions,@Min(1) @Max(3) short replicas,Map<String,String> config) {}
    public record PartitionsInput(@Min(1) int partitions) {}
    @GetMapping("/topics") public Set<String> list() throws Exception { return admin.listTopics().names().get(10,TimeUnit.SECONDS); }
    @GetMapping("/topics/{name}") public Map<String,Object> describe(@PathVariable String name) throws Exception { var t=admin.describeTopics(List.of(name)).allTopicNames().get(10,TimeUnit.SECONDS).get(name);return Map.of("name",name,"partitions",t.partitions().stream().map(p->Map.of("partition",p.partition(),"leader",p.leader().id(),"replicas",p.replicas().stream().map(n->n.id()).toList(),"isr",p.isr().stream().map(n->n.id()).toList())).toList()); }
    @PostMapping("/topics") @ResponseStatus(HttpStatus.CREATED) public TopicInput create(@Valid @RequestBody TopicInput input) throws Exception { var topic=new NewTopic(input.name(),input.partitions(),input.replicas());if(input.config()!=null) topic.configs(input.config());admin.createTopics(List.of(topic)).all().get(10,TimeUnit.SECONDS);return input; }
    @DeleteMapping("/topics/{name}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String name) throws Exception { admin.deleteTopics(List.of(name)).all().get(10,TimeUnit.SECONDS); }
    @PutMapping("/topics/{name}/partitions") public Map<String,Integer> partitions(@PathVariable String name,@Valid @RequestBody PartitionsInput input) throws Exception { admin.createPartitions(Map.of(name,NewPartitions.increaseTo(input.partitions()))).all().get(10,TimeUnit.SECONDS);return Map.of("partitions",input.partitions()); }
    @GetMapping("/topics/{name}/config") public Map<String,String> config(@PathVariable String name) throws Exception { var resource=new ConfigResource(ConfigResource.Type.TOPIC,name);var result=new TreeMap<String,String>();for(var entry:admin.describeConfigs(List.of(resource)).all().get(10,TimeUnit.SECONDS).get(resource).entries()) if(!entry.isSensitive()&&entry.value()!=null) result.put(entry.name(),entry.value());return result; }
    @PutMapping("/topics/{name}/config") public Map<String,String> configure(@PathVariable String name,@RequestBody Map<String,String> input) throws Exception { var ops=input.entrySet().stream().map(e->new AlterConfigOp(new ConfigEntry(e.getKey(),e.getValue()),AlterConfigOp.OpType.SET)).toList();admin.incrementalAlterConfigs(Map.of(new ConfigResource(ConfigResource.Type.TOPIC,name),ops)).all().get(10,TimeUnit.SECONDS);return input; }
    @GetMapping("/cluster") public Map<String,Object> cluster() throws Exception { var c=admin.describeCluster();return Map.of("clusterId",c.clusterId().get(10,TimeUnit.SECONDS),"brokers",c.nodes().get(10,TimeUnit.SECONDS).stream().map(n->Map.of("id",n.id(),"host",n.host(),"port",n.port())).toList()); }
}
