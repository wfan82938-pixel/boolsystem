package com.example.bloodsystem.controller;

import com.example.bloodsystem.entity.Donor;
import com.example.bloodsystem.service.DonorService;
import com.example.bloodsystem.service.DonorService.MatchResult;
import com.example.bloodsystem.service.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DonorController {

    @Autowired
    private DonorService service;

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "15") int size,
                        @RequestParam(required = false) String keyword) {
        Page<Donor> p = service.getDonors(page, size, keyword);
        model.addAttribute("donorPage", p);
        model.addAttribute("donors", p.getContent());
        model.addAttribute("keyword", keyword);
        return "index";
    }

    @GetMapping("/match")
    public String matchPage() { return "match"; }

    @GetMapping("/add")
    public String add(Model model) {
        model.addAttribute("donor", new Donor());
        return "add_donor";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable String id, Model model) {
        Donor d = service.getDonorById(id);
        if (d == null) return "redirect:/";
        model.addAttribute("donor", d);
        return "add_donor";
    }

    @GetMapping("/import")
    public String imp() { return "import_data"; }

//    @PostMapping("/save")
//    public String save(@ModelAttribute Donor donor, RedirectAttributes redirectAttributes) {
//        try {
//            service.saveDonor(donor);
//            redirectAttributes.addFlashAttribute("successMessage", "保存成功");
//        } catch (ObjectOptimisticLockingFailureException e) {
//            redirectAttributes.addFlashAttribute("errorMessage", "保存失败：该数据已被其他人修改，请刷新重试！");
//        } catch (Exception e) {
//            redirectAttributes.addFlashAttribute("errorMessage", "保存失败：" + e.getMessage());
//        }
//        return "redirect:/";
//    }

    @PostMapping("/save")
    public String save(@ModelAttribute Donor donor, Model model, RedirectAttributes redirectAttributes) {
        try {
            service.saveDonor(donor);
            redirectAttributes.addFlashAttribute("successMessage", "保存成功");
            return "redirect:/";
        } catch (ObjectOptimisticLockingFailureException e) {
            // 🔥 优化：发生冲突时留在当前页面，保留用户填写的数据
            model.addAttribute("errorMessage", "保存失败：该数据已被其他人修改，请刷新页面获取最新版本！");
            return "add_donor";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "保存失败：" + e.getMessage());
            return "add_donor";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            service.deleteDonor(id);
            redirectAttributes.addFlashAttribute("successMessage", "删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/reset")
    public String reset(RedirectAttributes redirectAttributes) {
        try {
            service.deleteAllDonors();
            redirectAttributes.addFlashAttribute("successMessage", "数据库已清空");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/import")
    public String impPost(@RequestParam("textData") String t, Model m) {
        ImportResult result = service.importFromText(t);
        StringBuilder msg = new StringBuilder();
        msg.append("成功导入 ").append(result.getSuccessCount()).append(" 条数据。");
        if (result.getFailureCount() > 0) {
            msg.append(" 失败 ").append(result.getFailureCount()).append(" 条。");
            msg.append(" <br/>错误详情（前100条）：<br/>");
            msg.append(String.join("<br/>", result.getErrorMessages()));
        }
        m.addAttribute("message", msg.toString());
        return "import_data";
    }

    @PostMapping("/api/match")
    @ResponseBody
    public List<MatchResult> apiMatch(@RequestParam(required = false) String bloodType,
                                      @RequestParam(required = false) String antibodies,
                                      @RequestParam(required = false, defaultValue = "false") boolean limitResult,
                                      @RequestParam Map<String, String> allParams) {
        return service.matchDonors(bloodType, parseParams(allParams), antibodies, limitResult);
    }

    private Map<String, String> parseParams(Map<String, String> allParams) {
        Map<String, String> map = new HashMap<>();
        if (allParams != null) {
            allParams.forEach((k, v) -> {
                if (v == null || v.trim().isEmpty()) return;

                if (k.startsWith("hpa")) {
                    String number = k.substring(3);
                    map.put("HPA-" + number, v);
                }

                if (k.equals("hlaA1")) map.put("HLA-A1", v.trim());
                if (k.equals("hlaA2")) map.put("HLA-A2", v.trim());
                if (k.equals("hlaB1")) map.put("HLA-B1", v.trim());
                if (k.equals("hlaB2")) map.put("HLA-B2", v.trim());
            });
        }
        return map;
    }
}