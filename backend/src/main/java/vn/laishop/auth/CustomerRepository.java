package vn.laishop.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.laishop.model.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
}
