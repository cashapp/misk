import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js";
window.Stimulus = Application.start();

Stimulus.register("search-bar", class extends Controller {
  search(ev) {
    const input = ev.target.value.toLowerCase();
    document.querySelectorAll(".registration").forEach(registration => {
      let hide = true;
      registration.querySelectorAll("*").forEach(col => {
        if (col.textContent.toLowerCase().includes(input)) {
          hide = false;
        }
      });
      if (hide) {
        registration.classList.contains("hidden") || registration.classList.add("hidden");
      } else {
        registration.classList.remove("hidden");
      }
    })
  }
})
