package ch.bzz.controller;

import ch.bzz.entity.Project;
import ch.bzz.generated.api.ProjectApi;
import ch.bzz.generated.model.LoginProject200Response;
import ch.bzz.generated.model.LoginRequest;
import ch.bzz.repository.ProjectRepository;
import ch.bzz.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
public class ProjectApiController implements ProjectApi {

    private final ProjectRepository projectRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    public ProjectApiController(ProjectRepository projectRepository, JwtUtil jwtUtil) {
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public ResponseEntity<Void> createProject(LoginRequest loginRequest) {
        String projectName = loginRequest.getProjectName();
        String password = loginRequest.getPassword();

        if (projectName == null || projectName.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name and password must not be blank");
        }
        if (projectRepository.existsById(projectName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project already exists");
        }

        Project project = new Project(projectName, encoder.encode(password));
        projectRepository.save(project);
        log.info("Created project '{}'", projectName);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<LoginProject200Response> loginProject(LoginRequest loginRequest) {
        String projectName = loginRequest.getProjectName();
        String password = loginRequest.getPassword();

        Project project = projectRepository.findById(projectName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!encoder.matches(password, project.getPasswordHash())) {
            log.warn("Failed login attempt for project '{}'", projectName);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtUtil.generateToken(project.getProjectName());
        log.info("Project '{}' logged in successfully", projectName);
        return ResponseEntity.ok(new LoginProject200Response().accessToken(token));
    }
}
