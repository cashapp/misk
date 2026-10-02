import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js"
window.Stimulus = Application.start()

Stimulus.register("database", class extends Controller {
  static targets = ["dbSelect"]

  selectDb(ev) {
    // Auto-submit the form when the database selector changes
    ev.target.closest("form").submit()
  }
})
