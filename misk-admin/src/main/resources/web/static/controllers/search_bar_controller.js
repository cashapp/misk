import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js";
window.Stimulus = Application.start();

Stimulus.register("search-bar", class extends Controller {
  search(ev) {
    const input = ev.target.value.toLowerCase();

    // Hide or show all registrations if they match input
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

    // Only show registration groups that have visible registrations
    document.querySelectorAll(".registration-group").forEach(registrationGroup => {
      const hasVisibleRegistration = Array.from(registrationGroup.querySelectorAll(".registration"))
        .some(registration => !registration.classList.contains("hidden"));
      const toggleableDiv = registrationGroup.querySelector('#toggle-container-contents');

      if (hasVisibleRegistration) {
        registrationGroup.classList.remove("hidden");

        if (input != "" && toggleableDiv && toggleableDiv.classList.contains("hidden")) {
          // Open all registration groups when there is search input
          registrationGroup.querySelector("button").click();
        } else if (input === "" && toggleableDiv && !toggleableDiv.classList.contains("hidden")) {
          // Close all registration groups when there is no search input
          registrationGroup.querySelector("button").click();
        }
      } else {
        registrationGroup.classList.add("hidden");
      }
    });
  }
})
