package br.com.pastelariass.relatorio;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Relatórios. Só o administrador consulta: margem, resultado e repasses são
 * números do dono do negócio, não da equipe do balcão. A trava fica na classe
 * inteira, então um endpoint novo aqui já nasce protegido.
 *
 * O período é sempre "de data até data", inclusive nas duas pontas. Dia é
 * inicio = fim; semana e mês são só intervalos de datas que a tela monta.
 *
 *   GET /api/relatorios/pastelaria?inicio=...&fim=...
 *   GET /api/relatorios/peixaria?inicio=...&fim=...
 *   GET /api/relatorios/geral?inicio=...&fim=...
 *   GET /api/relatorios/peixaria/compras-mensais?ano=2026
 */
@RestController
@RequestMapping("/api/relatorios")
@PreAuthorize("hasRole('ADMIN')")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/pastelaria")
    public RelatorioService.RelatorioPastelaria pastelaria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorioService.pastelaria(inicio, fim);
    }

    @GetMapping("/peixaria")
    public RelatorioService.RelatorioPeixaria peixaria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorioService.peixaria(inicio, fim);
    }

    @GetMapping("/geral")
    public RelatorioService.RelatorioGeral geral(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorioService.geral(inicio, fim);
    }

    @GetMapping("/peixaria/compras-mensais")
    public RelatorioService.ComprasMensaisPeixe comprasMensais(@RequestParam int ano) {
        return relatorioService.comprasMensaisPeixe(ano);
    }
}
