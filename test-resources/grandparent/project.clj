(defproject grandparent "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :modules {:inherited {:omit-source false
                        :foo [:inherited]}
            :versions {:ver "1.0"
                       x/x "1.0.1"
                       y/y "1.0.2"
                       scope/scope "9.9.9"
                       grandparent :ver
                       parent      :ver}}
  :foo [:root]
  :profiles {:dev {:foo [:dev]}
             :provided {:foo [:provided]}})
