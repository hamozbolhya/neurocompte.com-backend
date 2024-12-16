package com.pacioli.core.services.serviceImp;


import com.pacioli.core.models.Permission;
import com.pacioli.core.repositories.PermissionRepository;
import com.pacioli.core.services.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionServiceImpl implements PermissionService {


    @Autowired
    private PermissionRepository permissionRepository;

    @Override
    public List<Permission> getAllPermissions() {
        // Fetch all permissions sorted by name, for example
        return permissionRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

}
