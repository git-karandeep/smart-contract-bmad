package ai.generalcontracts.startup.model;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "orders")
public class Orders {

    @Id
    private String id;

    @NotBlank
    private String customer;

    @NotNull
    @Positive
    private Double amount;

    @NotBlank
    private String status;
}