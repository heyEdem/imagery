package com.Imagery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
class ImageController {

    private final ImageService imageService;

    @GetMapping("/")
    public String dashboard(Model model, @RequestParam(defaultValue = "1") int page) {
        List<String> images = imageService.listImages(page, 10);
        model.addAttribute("images", images);
        return "dashboard";
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            imageService.uploadImage(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "redirect:/";
    }

}
