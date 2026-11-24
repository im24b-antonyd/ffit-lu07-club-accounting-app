package ch.bzz.repository;

import ch.bzz.entity.Booking;
import ch.bzz.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByProject(Project project);

    List<Booking> findByProjectOrderByBookingNumberAsc(Project project);

    Optional<Booking> findByProjectAndBookingNumber(Project project, Integer bookingNumber);
}
