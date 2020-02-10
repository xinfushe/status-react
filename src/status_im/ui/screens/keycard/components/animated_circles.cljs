(ns status-im.ui.screens.keycard.components.animated-circles
  (:require [status-im.ui.components.react :as react]
            [reagent.core :as reagent]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.components.animation :as animation]
            [status-im.hardwallet.card :as keycard-nfc]
            [status-im.react-native.resources :as resources]
            [status-im.ui.components.colors :as colors]))

(defn circle [{:keys [animation-value color size]}]
  [react/animated-view
   {:style {:width            size
            :height           size
            :position         "absolute"
            :background-color color
            :border-radius    (/ size 2)
            :opacity          (animation/interpolate
                               animation-value
                               {:inputRange [0 1 2]
                                :outputRange [0.7 1 0]})
            :transform        [{:scale (animation/interpolate
                                        animation-value
                                        {:inputRange  [0 1]
                                         :outputRange [0.9 1]})}]}}])

(defn indicator-container [anim children]
  [react/animated-view
   {:style {:position         "absolute"
            :justify-content  :center
            :align-items      :center
            :border-radius    21
            :width            42
            :height           42
            :top              16.5
            :right            -24
            :shadow-offset    {:width 0 :height 2}
            :shadow-radius    16
            :elevation        8
            :shadow-opacity   0.1
            :shadow-color     "gba(0, 9, 26, 0.12)"
            :background-color :white
            :transform        [{:scale (animation/interpolate
                                        anim
                                        {:inputRange  [0 1]
                                         :outputRange [0 1]})}]}}
   children])

(defn indicator [{:keys [state animation-value]}]
  (case @state
    :error
    [indicator-container animation-value
     [vector-icons/icon :main-icons/close {:color  colors/red
                                           :height 28
                                           :width  28}]]
    :success
    [indicator-container animation-value
     [vector-icons/icon :main-icons/check {:color  colors/green
                                           :height 28
                                           :width  28}]]
    :connected
    [indicator-container animation-value
     [vector-icons/icon :main-icons/check {:color  colors/blue
                                           :height 28
                                           :width  28}]]
    :processing
    [indicator-container animation-value
     [react/activity-indicator {:color colors/blue}]]
    nil))

(defn animate-card-position [card-scale animation-value]
  {:transform [{:scale card-scale}
               {:translateX (animation/x animation-value)}
               {:translateY (animation/y animation-value)}]})

(defn card-colors [state]
  (case state
    (:init :awaiting)
    {:card-color "#2D2D2D"
     :key-color  "#27D8B9"
     :chip-color "#F0CC73"}
    (:connected :processing)
    {:card-color colors/blue
     :key-color  :white
     :chip-color :white}
    :success
    {:card-color colors/green
     :key-color  :white
     :chip-color :white}
    :error
    {:card-color colors/red
     :key-color  :white
     :chip-color :white}
    nil))

(defn card [{:keys [card-scale state indicator-value animation-value]}]
  (let [{:keys [card-color
                chip-color
                key-color]} (card-colors @state)]
    [react/animated-view
     {:style (merge
              (animate-card-position card-scale animation-value)
              {:height           80
               :width            120
               :border-radius    12
               :position         :absolute
               :shadow-offset    {:width 0 :height 2}
               :shadow-radius    16
               :elevation        8
               :shadow-opacity   0.1
               :shadow-color     "gba(0, 9, 26, 0.12)"
               :background-color card-color})}
     [react/animated-view
      {:style {:width            12
               :height           9
               :border-radius    3
               :left             19.5
               :top              30
               :background-color chip-color}}]
     [react/view
      {:style {:position        :absolute
               :justify-content :center
               :top             18
               :right           19.5
               :height          42
               :width           25}}
      [vector-icons/icon :main-icons/keycard-logo-big
       {:color  key-color
        :width  25
        :height 42}]]
     [indicator {:state           state
                 :animation-value indicator-value}]]))

(defn phone [{:keys [animation-value]}]
  [react/animated-view {:style {:position  :absolute
                                :bottom    0
                                :elevation 9
                                :opacity   (animation/interpolate
                                            animation-value
                                            {:inputRange  [0 1]
                                             :outputRange [0 0.9]})
                                :transform [{:translateY (animation/interpolate
                                                          animation-value
                                                          {:inputRange  [0 1]
                                                           :outputRange [125 10]})}]}}
   [react/image
    {:source (resources/get-image :onboarding-phone)
     :style  {:height 125
              :width  86}}]])

(def circle-easing    (animation/bezier 0.455 0.03 0.515 0.955))
(def card-easing      (animation/bezier 0.77 0 0.175 1))

(defn awaiting [{:keys [card card-scale phone state]}]
  (reset! state :awaiting)
  (let [card-loop     (animation/anim-loop
                       (animation/anim-sequence
                        [(animation/timing card
                                           {:toValue  #js {:x -30
                                                           :y 30}
                                            :duration 1000
                                            :easing   card-easing})
                         (animation/timing card
                                           {:toValue  {:x 45
                                                       :y 65}
                                            :duration 1000
                                            :delay    2000
                                            :easing   card-easing})
                         (animation/timing card
                                           {:toValue  #js {:x -30
                                                           :y 105}
                                            :duration 1000
                                            :delay    2000
                                            :easing   card-easing})
                         (animation/anim-delay 2000)]))
        phone-entrace (animation/parallel
                       [(animation/timing card-scale
                                          {:toValue  0.528
                                           :duration 1000
                                           :easing   card-easing})
                        (animation/timing phone
                                          {:toValue  1
                                           :duration 1000
                                           :easing   card-easing})
                        card-loop])]
    (animation/start phone-entrace)))

(defn start-animation
  [{:keys [small medium big card-scale phone
           indicator state]
    :as   opts}]
  (reset! state :init)
  (animation/set-value indicator 0)
  (animation/set-value phone 0)
  (let [phone-enter-at   7000
        circle-animation #(animation/timing %1 {:toValue  %2
                                                :delay    %3
                                                :duration 1000
                                                :easing   circle-easing})
        resets           (animation/timing card-scale
                                           {:toValue  0.66
                                            :duration 1000
                                            :easing   card-easing})
        circles          (animation/anim-loop
                          (animation/parallel
                           [(animation/anim-sequence
                             [(circle-animation small 1 0)
                              (circle-animation small 0 0)])
                            (animation/anim-sequence
                             [(circle-animation medium 1 200)
                              (circle-animation medium 0 0)])
                            (animation/anim-sequence
                             [(circle-animation big 1 400)
                              (circle-animation big 0 0)])]))
        animation        (animation/parallel
                          [resets
                           circles])]
    (js/setTimeout #(when (= @state :init)
                      (awaiting opts)) phone-enter-at)
    (animation/start animation)))

(defn on-error [{:keys [state restart]}]
  (reset! state :error)
  ;; Re-run animation only if state wasn't cahnged before
  (js/setTimeout #(when (= @state :error)
                    (restart)) 3000))

(defn on-processing [{:keys [state]}]
  (reset! state :processing))

(defn on-success [{:keys [state]}]
  (reset! state :success))

(defn on-connect
  [{:keys [state card small indicator processing
           medium big card-scale phone]}]
  (reset! state :connected)
  (let [connect-animation (animation/parallel
                           [(animation/timing card-scale
                                              {:toValue 1
                                               :timing  1000
                                               :easing  card-easing})
                            (animation/timing indicator
                                              {:toValue 1
                                               :timing  1000
                                               :easing  card-easing})
                            (animation/timing small
                                              {:toValue 2
                                               :timing  1000
                                               :easing  circle-easing})
                            (animation/timing medium
                                              {:toValue 2
                                               :timing  1000
                                               :easing  circle-easing})
                            (animation/timing big
                                              {:toValue 2
                                               :timing  1000
                                               :easing  circle-easing})
                            (animation/timing phone
                                              {:toValue 0
                                               :timing  1000
                                               :easing  card-easing})
                            (animation/timing card
                                              {:toValue #js {:x 0
                                                             :y 0}
                                               :timing  3000
                                               :easing  card-easing})])]
    (js/setTimeout #(when (= @state :connected)
                      (processing)) 2000)
    (animation/start connect-animation)))

(defn animated-circles [{:keys [state on-card-connected on-card-disconnected]}]
  (let [animation-small     (animation/create-value 0)
        animation-medium    (animation/create-value 0)
        animation-big       (animation/create-value 0)
        animation-card      (animation/create-value-xy #js {:x 0
                                                            :y 0})
        card-scale          (animation/create-value 0.66)
        animation-phone     (animation/create-value 0)
        animation-indicator (animation/create-value 0)
        on-start-animation  #(start-animation
                              {:state      state
                               :small      animation-small
                               :medium     animation-medium
                               :big        animation-big
                               :phone      animation-phone
                               :card       animation-card
                               :card-scale card-scale
                               :indicator  animation-indicator})

        on-card-processing #(on-processing {:state state})
        on-card-connected  #(do
                              (on-card-connected)
                              (on-connect
                               {:state      state
                                :processing on-card-processing
                                :indicator  animation-indicator
                                :card       animation-card
                                :card-scale card-scale
                                :phone      animation-phone
                                :small      animation-small
                                :medium     animation-medium
                                :big        animation-big}))
        on-success         #(on-success
                             {:state state})
        on-error           #(do
                              (on-card-disconnected)
                              (on-error
                               {:state   state
                                :restart on-start-animation}))]
    (reagent/create-class
     {:component-did-mount
      (fn []
        (keycard-nfc/remove-event-listeners)
        (keycard-nfc/on-card-connected on-card-connected)
        (keycard-nfc/on-card-disconnected on-error)
        (on-start-animation))
      :component-will-unmount
      (fn []
        (keycard-nfc/remove-event-listeners))
      :render
      (fn []
        [react/view {:style {:position        :absolute
                             :top             0
                             :bottom          0
                             :left            0
                             :right           0
                             :justify-content :center
                             :align-items     :center}}

         [circle {:animation-value animation-big
                  :size            200
                  :color           "#F1F4FF"}]
         [circle {:animation-value animation-medium
                  :size            140
                  :color           "#E3E8FA"}]
         [circle {:animation-value animation-small
                  :size            80
                  :color           "#D2D9F0"}]

         [card {:animation-value animation-card
                :state           state
                :indicator-value animation-indicator
                :card-scale      card-scale}]

         [phone {:animation-value animation-phone}]])})))
