package com.example.historique.controller;

import com.example.historique.client.CalculClient;
import com.example.historique.model.Operation;
import com.example.historique.service.OperationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private static final List<String> OPERATEURS =
            List.of("somme", "soustraction", "produit", "division");

    private final OperationService operationService;
    private final CalculClient calculClient;

    public OperationController(OperationService operationService,
                               CalculClient calculClient) {
        this.operationService = operationService;
        this.calculClient = calculClient;
    }

    // Demande le résultat à calcul-service et le range dans l'opération.
    // Retourne une réponse d'erreur si le calcul est impossible, null sinon.
    private ResponseEntity<Object> calculer(Operation op) {
        if (op.getOperateur() == null || !OPERATEURS.contains(op.getOperateur())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", "Opérateur inconnu"));
        }
        try {
            double r = calculClient.calculer(op.getOperateur(), op.getA(), op.getB());
            op.setResultat(r);
            return null;
        } catch (HttpClientErrorException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", "Calcul impossible (division par 0 ?)"));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                  .body(Map.of("erreur", "calcul-service est indisponible"));
        }
    }

    // POST /operations : crée une opération
    @PostMapping
    public ResponseEntity<Object> creer(@RequestBody Operation op) {
        ResponseEntity<Object> erreur = calculer(op);
        if (erreur != null) {
            return erreur;
        }
        Operation cree = operationService.ajouter(op);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(cree.getId()).toUri();
        return ResponseEntity.created(location).body(cree);
    }

    // GET /operations : retourne toutes les opérations
    @GetMapping
    public List<Operation> lister() {
        return operationService.lister();
    }

    // GET /operations/{id} : retourne une opération
    @GetMapping("/{id}")
    public ResponseEntity<Operation> lire(@PathVariable("id") int id) {
        Operation op = operationService.lire(id);
        if (op == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(op);
    }

    // PUT /operations/{id} : modifie une opération (F.8)
    @PutMapping("/{id}")
    public ResponseEntity<Object> modifier(@PathVariable("id") int id,
                                           @RequestBody Operation op) {
        if (!operationService.existe(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erreur", "Opération introuvable"));
        }
        ResponseEntity<Object> erreur = calculer(op);
        if (erreur != null) {
            return erreur;
        }
        operationService.remplacer(id, op);
        return ResponseEntity.ok(op);
    }

    // DELETE /operations/{id} : supprime une opération (F.9)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> supprimer(@PathVariable("id") int id) {
        if (operationService.supprimer(id) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erreur", "Opération introuvable"));
        }
        return ResponseEntity.noContent().build();
    }
}