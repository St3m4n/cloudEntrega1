package cl.duoc.pedidos360.kafkaadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Administración de consumer groups: listar, inspeccionar miembros y lag, mover offsets y eliminar grupos vacíos. */
@RestController
@RequestMapping("/api/admin/kafka/consumer-groups")
public class ConsumerGroupsController {
    private static final long TIMEOUT = 10;
    private final AdminClient admin;

    public ConsumerGroupsController(AdminClient admin) {
        this.admin = admin;
    }

    public record GroupSummary(String groupId, String state, boolean simple) {}
    public record Member(String memberId, String clientId, String host, List<String> partitions) {}
    public record PartitionLag(String topic, int partition, Long committedOffset, long endOffset, Long lag, String clientId) {}
    public record GroupDetail(String groupId, String state, String assignor, String coordinator, List<Member> members,
                              List<PartitionLag> partitions, long totalLag) {}
    public record ResetInput(@NotBlank String topic, @NotBlank @Pattern(regexp = "earliest|latest") String position) {}

    @GetMapping
    public List<GroupSummary> list() throws Exception {
        return admin.listConsumerGroups().all().get(TIMEOUT, TimeUnit.SECONDS).stream()
                .map(g -> new GroupSummary(g.groupId(), g.state().map(Enum::name).orElse("UNKNOWN"), g.isSimpleConsumerGroup()))
                .sorted(Comparator.comparing(GroupSummary::groupId))
                .toList();
    }

    @GetMapping("/{groupId}")
    public GroupDetail describe(@PathVariable String groupId) throws Exception {
        ConsumerGroupDescription description = describeGroup(groupId);
        Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata().get(TIMEOUT, TimeUnit.SECONDS);
        Map<TopicPartition, MemberDescription> owners = new HashMap<>();
        List<Member> members = new ArrayList<>();
        for (var member : description.members()) {
            var assigned = member.assignment().topicPartitions();
            assigned.forEach(tp -> owners.put(tp, member));
            members.add(new Member(member.consumerId(), member.clientId(), member.host(),
                    assigned.stream().map(TopicPartition::toString).sorted().toList()));
        }
        var partitions = new TreeSet<TopicPartition>(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition));
        partitions.addAll(committed.keySet());
        partitions.addAll(owners.keySet());
        Map<TopicPartition, OffsetSpec> request = partitions.stream().collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResultInfo> ends = request.isEmpty() ? Map.of()
                : admin.listOffsets(request).all().get(TIMEOUT, TimeUnit.SECONDS);
        List<PartitionLag> lags = new ArrayList<>();
        long total = 0;
        for (var tp : partitions) {
            long end = ends.containsKey(tp) ? ends.get(tp).offset() : 0;
            var offset = committed.get(tp);
            Long current = offset == null ? null : offset.offset();
            Long lag = current == null ? null : Math.max(0, end - current);
            if (lag != null) total += lag;
            var owner = owners.get(tp);
            lags.add(new PartitionLag(tp.topic(), tp.partition(), current, end, lag, owner == null ? null : owner.clientId()));
        }
        var coordinator = description.coordinator();
        return new GroupDetail(groupId, description.state().name(), description.partitionAssignor(),
                coordinator == null ? null : coordinator.id() + "@" + coordinator.host() + ":" + coordinator.port(),
                members, lags, total);
    }

    /** Mueve los offsets del grupo al inicio o al final del tópico (reprocesar o saltar). Requiere el grupo sin consumidores. */
    @PostMapping("/{groupId}/offsets")
    public Map<String, Object> reset(@PathVariable String groupId, @Valid @RequestBody ResetInput input) throws Exception {
        if (!describeGroup(groupId).members().isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El grupo tiene consumidores activos; deténgalos antes de mover offsets");
        var topic = admin.describeTopics(List.of(input.topic())).allTopicNames().get(TIMEOUT, TimeUnit.SECONDS).get(input.topic());
        OffsetSpec spec = input.position().equals("earliest") ? OffsetSpec.earliest() : OffsetSpec.latest();
        Map<TopicPartition, OffsetSpec> request = topic.partitions().stream()
                .collect(Collectors.toMap(p -> new TopicPartition(input.topic(), p.partition()), p -> spec));
        Map<TopicPartition, OffsetAndMetadata> target = admin.listOffsets(request).all().get(TIMEOUT, TimeUnit.SECONDS)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new OffsetAndMetadata(e.getValue().offset())));
        admin.alterConsumerGroupOffsets(groupId, target).all().get(TIMEOUT, TimeUnit.SECONDS);
        return Map.of("groupId", groupId, "topic", input.topic(), "position", input.position(), "partitions", target.size());
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String groupId) throws Exception {
        admin.deleteConsumerGroups(List.of(groupId)).all().get(TIMEOUT, TimeUnit.SECONDS);
    }

    private ConsumerGroupDescription describeGroup(String groupId) throws Exception {
        return admin.describeConsumerGroups(List.of(groupId)).describedGroups().get(groupId).get(TIMEOUT, TimeUnit.SECONDS);
    }
}
