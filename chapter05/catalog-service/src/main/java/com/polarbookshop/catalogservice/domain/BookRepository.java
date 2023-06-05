package com.polarbookshop.catalogservice.domain;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface BookRepository extends CrudRepository<Book, Long> {

	Optional<Book> findByIsbn(String isbn);
	boolean existsByIsbn(String isbn);

	@Modifying
	@Transactional
	// the :isbn is named parameter, reference: https://docs.oracle.com/cd/E19226-01/820-7627/bnbrh/index.html
	@Query("delete from Book where isbn = :isbn")
	void deleteByIsbn(String isbn);

}
