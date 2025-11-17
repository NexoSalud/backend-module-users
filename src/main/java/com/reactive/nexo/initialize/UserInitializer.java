package com.reactive.nexo.initialize;

import com.reactive.nexo.model.AttributeUser;
import com.reactive.nexo.model.User;
import com.reactive.nexo.repository.AttributeUserRepository;
import com.reactive.nexo.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("!test")
@Slf4j
public class UserInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttributeUserRepository attributeUserRepository;
    @Autowired
    private com.reactive.nexo.repository.ValueAttributeUserRepository valueAttributeUserRepository;

    @Autowired
    private com.reactive.nexo.service.ValueAttributeService valueAttributeService;
    
    @Override
    public void run(String... args) {
            initialDataSetup();
    }

    private List<User> getData(){
        return Arrays.asList(new User(null,"Juan","Campo","CC","1"),
                             new User(null,"Elieser","Banguero","CC","2"),
                             new User(null,"Migel","Caicedo","CC","3"),
                             new User(null,"Huber","Guaza","CC","4"),
                             new User(null,"Kenner","Zambrano","CC","5"),
                             new User(null,"Yeider","Caicedo","CC","6"),
                             new User(null,"Jhordy","Abonia","CC","7"));
    }

    /*private List<Department> getDepartments(){
        return Arrays.asList(new Department(null,"Mechanical",1,"Mumbai"),
                new Department(null,"Computer",2,"Bangalore"));
    }*/

    private void initialDataSetup() {
        userRepository.deleteAll()
                .thenMany(Flux.fromIterable(getData()))
                .flatMap(userRepository::save)
                .collectList()
                .flatMap(savedUsers -> {
                    // create attributes for each saved user
                    List<AttributeUser> attrs = new java.util.ArrayList<>();
                    for(com.reactive.nexo.model.User u : savedUsers){
                        attrs.add(new AttributeUser(null,"fecha de nacimiento",false, u.getId()));
                        attrs.add(new AttributeUser(null,"lugar de nacimiento ciudad",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"lugar de nacimiento departamento",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"lugar de nacimiento pais",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"ubicacion ciudad",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"ubicacion departamento",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"ubicacion pais",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"entidad de salud",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"ultima consulta",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"telefono",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"email",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"regimen",false,  u.getId()));
                        // clinical history example attributes
                        attrs.add(new AttributeUser(null,"historia_clinica_numero",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"diagnostico_principal",false,  u.getId()));
                        attrs.add(new AttributeUser(null,"alergias",true,  u.getId()));
                    }
                    return attributeUserRepository.saveAll(Flux.fromIterable(attrs)).collectList();
                })
                .flatMap(savedAttrs -> {
                    // create value entries for each attribute saved
                    List<com.reactive.nexo.model.ValueAttributeUser> vals = new java.util.ArrayList<>();
                    for(com.reactive.nexo.model.AttributeUser a : savedAttrs){
                        String attr = a.getName_attribute();
                        String val;
                        switch(attr){
                            case "fecha de nacimiento": val = "1992-05-06"; break;
                            case "lugar de nacimiento ciudad": val = "cali"; break;
                            case "lugar de nacimiento departamento": val = "valle"; break;
                            case "lugar de nacimiento pais": val = "colombia"; break;
                            case "ubicacion ciudad": val = "guachene"; break;
                            case "ubicacion departamento": val = "cauca"; break;
                            case "ubicacion pais": val = "colombia"; break;
                            case "entidad de salud": val = "sura"; break;
                            case "ultima consulta": val = "2024-06-06"; break;
                            case "telefono": val = "315-000-0000"; break;
                            case "email": val = "jhon-doe@test.co"; break;
                            case "regimen": val = "subcidiado"; break;
                            case "historia_clinica_numero": val = "HC-1000" + a.getId(); break;
                            case "diagnostico_principal": val = "Hipertension"; break;
                            case "alergias": val = "Ninguna"; break;
                            default: val = "";
                        }
                        vals.add(new com.reactive.nexo.model.ValueAttributeUser(null, a.getId(), val));
                    }
                    // use service to enforce 'multiple' rule per attribute
                    return Flux.fromIterable(vals)
                            .flatMap(v -> valueAttributeService.saveValue(v))
                            .collectList();
                })
                .thenMany(userRepository.findAll())
                .subscribe(user -> {
                    log.info("User Inserted from CommandLineRunner " + user);
                });
    }

}
