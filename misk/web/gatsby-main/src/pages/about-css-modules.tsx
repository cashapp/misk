import { Link } from "gatsby"
import React from "react"
import Container from "../components/container"
import Layout from "../layouts"
import styles from "./about-css-modules.module.css"

export interface UserProps {
  username: string,
  avatar: string,
  excerpt: string,
}

class User extends React.Component<UserProps> {
  constructor(props: UserProps) {
    super(props)
  }
  render() {
    return(
      <div className={styles.user}>
        <img src={this.props.avatar} className={styles.avatar} alt="" />
        <div className={styles.description}>
          <h2 className={styles.username}>
            {this.props.username}
          </h2>
          <p className={styles.excerpt}>
            {this.props.excerpt}
          </p>
        </div>
      </div>
    )
  }
}

export default () =>
  <Layout>
    <Container>
      <h1>About CSS Modules</h1>
      <p>CSS Modules are cool</p>
      <Link to="/">Home</Link>
      <User
        username="Jane Doe"
        avatar="https://s3.amazonaws.com/uifaces/faces/twitter/adellecharles/128.jpg"
        excerpt="I'm Jane Doe. Lorem ipsum dolor sit amet, consectetur adipisicing elit."
      />
      <User
        username="Bob Smith"
        avatar="https://s3.amazonaws.com/uifaces/faces/twitter/vladarbatov/128.jpg"
        excerpt="I'm Bob smith, a vertically aligned type of guy. Lorem ipsum dolor sit amet, consectetur adipisicing elit."
      />
    </Container>
  </Layout>