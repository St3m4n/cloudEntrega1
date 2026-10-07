package cl.duoc.pedidos360.common;
import jakarta.persistence.*;
@Entity @Table(name="p360_documents", indexes=@Index(name="p360_kind_idx",columnList="kind"))
public class Document {
    @Id @Column(length=160) public String id;
    @Column(nullable=false,length=40) public String kind;
    @Lob @Column(nullable=false) public String payload;
    @Version public Long revision;
    public Document() {}
    public Document(String id,String kind,String payload) { this.id=id;this.kind=kind;this.payload=payload; }
}
