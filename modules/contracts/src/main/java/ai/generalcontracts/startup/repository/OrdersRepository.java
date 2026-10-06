package ai.generalcontracts.startup.repository;


import ai.generalcontracts.startup.model.Orders;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrdersRepository extends MongoRepository<Orders, String> {
}