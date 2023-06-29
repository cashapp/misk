import { Application, Controller } from "../cache/stimulus/3.1.0/stimulus.min.js"
import { useClickOutside, useTransition } from "../cache/stimulus-use/0.52.0/index.umd.min.js"
window.Stimulus = Application.start()

/**
 * Usage
 * =====
 *
 * Docs: https://dev.to/phawk/hotwire-best-practices-for-stimulus-40e
 *
 * add data-controller="toggle" to common ancestor
 *
 * Action (add this to your button):
 * data-action="toggle#toggle"
 *
 * Targets (add this to the item to be shown/hidden):
 * data-toggle-target="toggleable" data-css-class="class-to-toggle"
 *
 * Example
 * <div data-controller="toggle">
 *   <button data-action="toggle#toggle />
 *   <div class="visible" data-toggle-target="toggleable" data-css-class="visible" >
 *      <p>More information</p>
 *   </div>
 * </div>
 */
Stimulus.register("toggle-click-outside", class extends Controller {
  static targets = ["toggleable"]

  connect() {
    // Any clicks outside the controller’s element can
    // be setup to either add a 'hidden' class or
    // remove a 'open' class etc.
    useClickOutside(this)
//    useTransition(this)
  }

  toggle(e) {
    e.preventDefault()

    this.toggleableTargets.forEach((target) => {
      target.classList.toggle(target.dataset.cssClass)
    })
  }

  clickOutside(e) {
    this.toggleableTargets.forEach((target) => {
      target.classList.add(target.dataset.cssClass)
    })
  }
})
