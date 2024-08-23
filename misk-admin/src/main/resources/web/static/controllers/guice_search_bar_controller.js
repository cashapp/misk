import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js";
window.Stimulus = Application.start();

Stimulus.register("search-bar", class extends Controller {
  search(ev) {
    const input = ev.target.value.toLowerCase();

    // Hide or show all bindings if they match input
    document.querySelectorAll(".binding").forEach(binding => {
      let hide = true;
      binding.querySelectorAll("*").forEach(col => {
        if (col.textContent.toLowerCase().includes(input)) {
          hide = false;
        }
      });
      if (hide) {
        binding.classList.contains("hidden") || binding.classList.add("hidden");
      } else {
        binding.classList.remove("hidden");
      }
    })

    // Only show binding groups that have visible bindings
    document.querySelectorAll(".binding-group").forEach(bindingGroup => {
      const hasVisiblebinding = Array.from(bindingGroup.querySelectorAll(".binding"))
        .some(binding => !binding.classList.contains("hidden"));
      const toggleableDiv = bindingGroup.querySelector('#toggle-container-contents');

      if (hasVisiblebinding) {
        bindingGroup.classList.remove("hidden");

        if (input != "" && toggleableDiv && toggleableDiv.classList.contains("hidden")) {
          // Open all binding groups when there is search input
          bindingGroup.querySelector("button").click();
        } else if (input === "" && toggleableDiv && !toggleableDiv.classList.contains("hidden")) {
          // Close all binding groups when there is no search input
          bindingGroup.querySelector("button").click();
        }
      } else {
        bindingGroup.classList.add("hidden");
      }
    });
  }
})
