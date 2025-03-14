package com.Imagery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class ImageController {

    @Autowired
    private ImageService imageService;

    @GetMapping("/")
    public String index(@RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "12") int size,
                        Model model) {

        Map<String, Object> result = imageService.getImages(page, size);

        model.addAttribute("images", result.get("images"));
        model.addAttribute("totalPages", result.get("totalPages"));
        model.addAttribute("currentPage", result.get("currentPage"));
        model.addAttribute("hasNextPage", result.get("hasNextPage"));
        model.addAttribute("pageSize", size);

        return "dashboard";
    }

    @GetMapping("/upload")
    public String addimage() {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadFiles(@RequestParam("images") MultipartFile[] files, RedirectAttributes redirectAttributes) {
        try {
            String response = imageService.uploadMultipleFiles(files);
            if (response.equals("success")) {
                redirectAttributes.addFlashAttribute("message", "Successfully uploaded");
            } else if (response.equals("empty")) {
                redirectAttributes.addFlashAttribute("message", "Please select a file to upload");
                return "redirect:/upload";
            } else if (response.equals("max")) {
                redirectAttributes.addFlashAttribute("message", "Upload file. Max number of images is 5");
                return "redirect:/upload";
            }
//            else if (response.equals("size")) {
//                redirectAttributes.addFlashAttribute("message", "Max image size is 1mb");
//                return "redirect:/upload";
//            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Failed to upload file: " + e.getMessage());
            return "redirect:/upload";
        }

        return "redirect:/";
    }

    @PostMapping("/delete")
    public String deleteImage(@RequestParam("imageKey") String imageKey, RedirectAttributes redirectAttributes) {
        String sanitizedKey = imageKey.split("\\?")[0]; // Remove query params
        boolean deleted = imageService.deleteImage(sanitizedKey);
//        boolean deleted = imageService.deleteImage(imageKey);
        System.out.println(imageKey);

        if (deleted) {
            redirectAttributes.addFlashAttribute("message", "Image deleted successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete image.");
        }

        return "redirect:/";
    }

}
