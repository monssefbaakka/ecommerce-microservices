package com.monssef.ecommerce.user;

import com.monssef.ecommerce.user.dto.UserDTO;
import org.mapstruct.Mapper;

// No RegisterRequest -> User here: the email is normalized and the password hashed by UserService
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDTO toDto(User user);
}
